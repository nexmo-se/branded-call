//
//  CallController.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 12/02/2023.
//

import UIKit
import CallKit
import PushKit
import Combine
import VonageClientSDKVoice

typealias CallStream = AnyPublisher<Call,Never>

protocol CallController {
    // Public Stream of calls to drive custom UIs
    var calls: AnyPublisher<CallStream,Never> { get }
    
    // Public Session
    var session: String? { get }
    
    // Public Mute State
    var isMuted: CurrentValueSubject<Bool, Never> { get }

    // report VOIP Push to callkit
    func reportVoipPush(_ notification:PKPushPayload)
    
    // Callkit actions initiated from custom application UI as opposed to System UI.
    func reportCXAction(_ cxaction:CXAction)
    
    // Provide Vonage Client with user JWT token for connection auth.
    func updateSessionToken(_ token:String?)
    
    // Register device notification tokens with Vonage
    func registerPushTokens(_ t:PushToken)
    
    // Special case for CXStartCallAction
    func startOutboundCall(_ context:[String:String]) -> UUID
}


// Private Implementation

class VonageCallController: NSObject {
    var cancellables = Set<AnyCancellable>()
    
    // VGClient
    let client: VGVoiceClient

    // We create a series of Subjects (imperative publishers) to help
    // organise the different delegate callbacks received from VGClient
    let vonageWillReconnect = PassthroughSubject<Void, Never>()
    let vonageDidReconnect = PassthroughSubject<Void, Never>()
    let vonageSessionError = PassthroughSubject<VGSessionErrorReason, Never>()
    let vonageSession = CurrentValueSubject<String?, Never>(nil)
    
    // We transform delegate callbacks into a 'hot' stream of call updates
    // and a 'cold' subject which allows clients to understand current active calls
    let vonageCalls = PassthroughSubject<Call, Never>()
    let vonageCallUpdates = PassthroughSubject<(UUID, CallStatus), Never>()
    var vonageActiveCalls = CurrentValueSubject<Dictionary<UUID,Call>,Never>([:])
    var vonageCallIsMuted = CurrentValueSubject<Bool, Never>(false)

    // Internal reactive storage for the token provided via `CallController.updateSessionToken()`
    private let vonageToken = CurrentValueSubject<String?,Never>(nil)
    
    // Callkit
    var callProvider: CXProvider!
    lazy var cxController = CXCallController()
    
    // Init
    init(client:VGVoiceClient){
        self.client = client
        super.init()
        client.delegate = self
        callProvider = initCXProvider()
        bindCallController()
        bindCallkit()
    }
}

extension VonageCallController: CallController {
    // Calls updates are 'demuxed' into seperate streams to help subscribers (like UIs)
    // concentrate on specific call updates
    var calls: AnyPublisher<CallStream,Never> {
        return vonageCalls.map { call in
            self.vonageCallUpdates
                .filter { $0.0 == call.id }
                .map {
                    Call(call: call, status: $0.1)
                }
                .prepend(call)
                .removeDuplicates(by: {a,b in a.status == b.status })
                .share()
                .eraseToAnyPublisher()
        }
        .share()
        .eraseToAnyPublisher()
    }
    
    var session: String? {
        return vonageSession.value
    }
    
    var isMuted: CurrentValueSubject<Bool, Never> {
        return vonageCallIsMuted
    }
    
    func reportVoipPush(_ notification: PKPushPayload) {
        self.client.processCallInvitePushData(notification.dictionaryPayload)
    }

    func reportCXAction(_ cxaction: CXAction) {
        cxController.requestTransaction(with: [cxaction], completion: { err in
            // TODO
        })
    }
    
    func updateSessionToken(_ token: String?) {
        vonageToken.value = token
    }
    
    // Normally we just forward all CXActions to Callkit
    // but we special case the start of outbound calls
    // so we can ensure the correct UUID can be provided to Callkit
    func startOutboundCall(_ context: [String : String]) -> UUID {
        let tid = UUID()
        
        let session = Future<String?,Error> { p in
            self.client.createSession(self.vonageToken.value ?? "") { err, session in
                p(err != nil ? Result.failure(err!) : Result.success(session!))
            }
        }
        let call = session.flatMap { _ in
            Future<String,Error> { p in
                self.client.serverCall(context) { err, callId in
                    p(err != nil ? Result.failure(err!) : Result.success(callId!))
                }
            }
            .first()
        }
            
        call.asResult()
            .sink { result in
            switch (result) {
            case .success(let callId):
                self.vonageCalls.send(
                    Call.outbound(id: UUID(uuidString: callId)!, to: context["to"] ?? "unknown", status: .ringing)
                )
            case .failure:
                // TODO:
                return
            }
        }
        .store(in: &cancellables)

        return tid
    }
    
    func registerPushTokens(_ t: PushToken) {
        vonageSession.compactMap {$0}.first().sink { _ in
            let deviceId = UserDefaults.standard.string(forKey: "DEVICE_ID")
            if (deviceId == nil) {
                self.client.registerVoipToken(t.voip, isSandbox: true) { err,device in
                    UserDefaults.standard.set(device, forKey: "DEVICE_ID")
                }
            }
        }
        .store(in: &cancellables)
    }
    
}

extension VonageCallController {
    
    private func initCXProvider()-> CXProvider {
        let config = CXProviderConfiguration()
        config.iconTemplateImageData = UIImage(named: "vonage-transparent.png")?.pngData()
        config.supportsVideo = false
        config.maximumCallsPerCallGroup = 1
        config.includesCallsInRecents = true
        config.supportedHandleTypes = [.generic, .phoneNumber]
        let provider = CXProvider(configuration: config)
        provider.setDelegate(self, queue: nil)
        return provider
    }
    
    func bindCallController() {
        vonageToken.dropFirst().filter { $0 == nil }.sink { _ in
            if (self.vonageSession.value != nil) {
                let deviceId = UserDefaults.standard.string(forKey: "DEVICE_ID")
                self.client.unregisterDeviceTokens(byDeviceId: deviceId!) { err in
                    if (err != nil) {
                        print("unregister error \(err?.localizedDescription)")
                    }
                    else {
                        UserDefaults.standard.removeObject(forKey: "DEVICE_ID")
                        self.client.deleteSession { error in
                            print("session delete: \(error?.localizedDescription ?? "successful")")
                            self.vonageSession.send(nil)
                        }
                    }
                }
                
        
            }
        }.store(in: &cancellables)
        
        vonageToken.compactMap { $0 }.filter { $0 != "" }.first().flatMap { _ in
            Future<String?,Error> { p in
                if (self.vonageSession.value == nil) {
                    self.client.createSession(self.vonageToken.value ?? "") { err, session in
                        p(err != nil ? Result.failure(err!) : Result.success(session!))
                    }
                }
            }
        }
        .asResult()
        .sink { result in
            switch(result){
            case .success(let s):
                self.vonageSession.send(s)
            case .failure:
                return // TODO
            }
        }
        .store(in: &cancellables)
        
        // Book keeping for active call
        self.calls
            .flatMap{ $0 }
            .scan(Dictionary<UUID,Call>()) { all, update  in
                var new = all
                if case .completed = update.status {
                    new.removeValue(forKey: update.id)
                }
                else {
                    new[update.id] = update
                }
                return new
            }
            .assign(to: \.value, on: vonageActiveCalls)
            .store(in: &cancellables)
        
        
        // Any time we receive a new inbound call, renew token ahead of time IFF needed
        self.vonageCalls
            .filter { if case .inbound = $0 { return true }; return false ;}
            .combineLatest(self.vonageActiveCalls)
            .filter { $0.1.count == 1 }
            .flatMap { _ in
                Future<String?,Error> { p in
                    guard let token =  UserDefaults.standard.string(forKey: "TOKEN") else {
                        return
                      }
                    
                    self.client.createSession(token) { err, session in
                        p(err != nil ? Result.failure(err!) : Result.success(session!))
                    }
                }
            }
            .replaceError(with: nil) // TODO
            .assign(to: \.value, on: vonageSession)
            .store(in: &cancellables)
        
    }
}
