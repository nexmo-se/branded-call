//
//  CallController+CXProviderDelegate.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 15/02/2023.
//

import Foundation
import CallKit
import VonageClientSDKVoice
import AudioToolbox

extension VonageCallController: CXProviderDelegate {
    
    func providerDidReset(_ provider: CXProvider) {
    }
    
    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        // We cheat a little bit with Outbound call starts -
        // 1. we create our vgcall first, so we can have the correct UUID
        // 2. We report to the cxcontroller afterwards
        // 3. here in the provider, we just call fulfill action right away
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction){
        guard let _ = self.vonageActiveCalls.value[action.callUUID]  else {
            action.fail()
            return
        }
        
        self.client.answer(action.callUUID.toVGCallID()) { err in
            guard err == nil else {
                // TODO:
                provider.reportCall(with: action.callUUID, endedAt: Date.now, reason: .failed)
                self.vonageCallUpdates.send((action.callUUID, .completed(remote: false, reason: .failed)))
                return
            }
            self.vonageCallUpdates.send((action.callUUID, .answered(time: Date.now)))
            action.fulfill()
        }
    }
    
    func provider(_ provider: CXProvider, perform action: CXEndCallAction){
        guard let call = self.vonageActiveCalls.value[action.callUUID]  else {
            action.fail()
            return
        }
                
        if case .inbound(_,_,.ringing) = call {
            self.client.reject(action.callUUID.toVGCallID()){ err in
                self.vonageCallUpdates.send((action.callUUID, CallStatus.completed(remote: false, reason: nil)))
                action.fulfill()
            }
        }
        else {
            self.client.hangup(action.callUUID.toVGCallID()){ err in
                action.fulfill()
            }
        }
    }
    
    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        guard let _ = self.vonageActiveCalls.value[action.callUUID]  else {
            action.fail()
            return
        }
        
        if (action.isMuted == true) {
            self.client.mute(action.callUUID.toVGCallID()) { err in
                self.vonageCallIsMuted.value = true
            }
        }
        else {
            self.client.unmute(action.callUUID.toVGCallID()) { err in
                self.vonageCallIsMuted.value = false
            }
        }
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession){
        VGVoiceClient.enableAudio(audioSession)
    }
    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession){
        VGVoiceClient.disableAudio(audioSession)
    }
}

extension VonageCallController {
    
    func bindCallkit() {
        
        self.calls
            .flatMap { $0 }
            .sink { call in
                switch (call) {
                case let .outbound(callId,to,status):
                    switch(status) {
                    case .ringing:
                        // Outbound calls need reporting to callkit
                        self.cxController.requestTransaction(
                            with: CXStartCallAction(call: callId, handle: CXHandle(type: .generic, value: to)),
                            completion: { err in
                                guard err == nil else {
                                    self.client.hangup(callId.toVGCallID()) { err in
                                        // Todo:
                                    }
                                    return
                                }
                                self.callProvider.reportOutgoingCall(with: callId, startedConnectingAt: Date.now)
                            }
                        )
                        
                    case .answered:
                        // Answers are remote by definition, so report them
                        self.callProvider.reportOutgoingCall(with: callId, connectedAt: Date.now)
                        
                    case .completed(true, .some(let reason)):
                        // Report Remote Hangups + Cancels
                        self.callProvider.reportCall(with: callId, endedAt: Date.now, reason: reason)
                        
                    default:
                        // Nothing needed to report for local hangups
                        return
                    }
                    
                case let .inbound(callId,from,status):
                    switch (status) {
                    case .ringing:
                        // Report new Inbound calls so we follow PushKit and Callkit Rules
                        let update = CXCallUpdate()

                        update.supportsDTMF = false
                        update.supportsHolding = false
                        update.supportsGrouping = false
                        update.hasVideo = false
                        update.remoteHandle = CXHandle(type: .generic, value: from)
                        update.localizedCallerName = from
                        
                        self.callProvider.reportNewIncomingCall(with: callId, update: update) { err in
                            if err != nil {
                                self.client.reject(callId.toVGCallID()){ err in
                                }
                            }
                        }
                        
                    case .completed(true,.some(let reason)):
                        // Report Remote Hangups + Cancels
                        self.callProvider.reportCall(with: callId, endedAt: Date.now, reason: reason)
                        
                    default:
                        // Nothing needed to report since answering requires local CXAction
                        // Same for local hangups
                        return
                    }
                }
            }
            .store(in: &cancellables)
    }
    
}
