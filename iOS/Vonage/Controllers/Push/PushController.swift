//
//  PushController.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 12/02/2023.
//

import Foundation
import PushKit
import UIKit
import Combine
import UserNotifications

typealias PushToken = (user:Data,voip:Data)

class PushController: NSObject {
    
    private let voipRegistry = PKPushRegistry(queue: nil)
    private var cancellables = Set<AnyCancellable>()

    // Delegate Subjects
    let pushKitToken = CurrentValueSubject<Data?,Never>(nil)
    let notificationToken = CurrentValueSubject<Data?,Never>(nil)
    let voipPush = PassthroughSubject<PKPushPayload,Never>()

    override init() {
        super.init()
    }
}

extension PushController {
    
    func initialisePushTokens() {
        
        NotificationCenter.default
            .publisher(for: NSNotification.didRegisterForRemoteNotificationNotification)
            .compactMap { n  in n.userInfo!["data"] as? Data?}
            .first()
            .sink {
                self.notificationToken.send($0)
            }
            .store(in: &cancellables)
 
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    //
                    UIApplication.shared.registerForRemoteNotifications()
                    //
                    self.voipRegistry.delegate = self
                    self.voipRegistry.desiredPushTypes = [PKPushType.voIP]
                }
            }
        }
    }
}


extension PushController: PKPushRegistryDelegate {
        
    func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        if (type == PKPushType.voIP) {
            pushKitToken.send(pushCredentials.token)
        }
    }
    
    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
            
        switch (type){
        case .voIP:
            voipPush.send(payload)
        default:
            return
        }
        completion()
    }
    
}
