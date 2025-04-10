//
//  AppDelegate.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 25/01/2023.
//

import UIKit
import VonageClientSDKVoice
import CallKit
import Combine

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    
    var callController: CallController!
    var pushController: PushController!
    var userController: UserController!
    private var cancellables = Set<AnyCancellable>()
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Create Application Object Graph
        
        // Vonage Client is a stateful object and idelly should be initialised once
        // and shared between controllers
        let config = VGClientInitConfig(loggingLevel: .info, region: .AP)
        let vonageClient = VGVoiceClient(config)
        VGVoiceClient.isUsingCallKit = true
        
        // Simple 'manager' classes to orchestrate functionality
        // Its assumed integrating applications will already have their
        // own versions of 'pushController' and 'userController' already
        // Within the sample code we have naive/dummy implementations for both
        // to showcase typical integration
        pushController = PushController()
        userController = UserController()
        
        // The Call Controller is an example of an integration between Application
        // and the vonage sdk where we orchestrate the different delegate callbacks
        // into the relevant subsystems. In this sample we demonstrate what that would look
        // like in terms of a classic VOIP app using Callkit.
        callController = VonageCallController(client: vonageClient)
        
        // Bind Non UI related Controllers
        // Its important to do this early in the app startup cylce so we can respond
        // to push notifications received when the app is not running
        bindControllers()
        
        // Setup Mic and Audio Session
        // Again its important to NOT tie this to any UI Component lifecycle
        // so we can handle voip calls when app is not running
        let mediaType = AVMediaType.audio
        let authorizationStatus = AVCaptureDevice.authorizationStatus(for: mediaType)
        switch authorizationStatus {
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: mediaType) { granted in
                print("🎤 access \(granted ? "granted" : "denied")")
            }
        case .authorized, .denied, .restricted:
            print("auth")
        @unknown default:
            break
        }

        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord)
        
        return true
    }
    
    func application(_ application: UIApplication, willFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        return true
    }

    // MARK: UISceneSession Lifecycle

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        // Called when a new scene session is being created.
        // Use this method to select a configuration to create the new scene with.
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
        // Called when the user discards a scene session.
        // If any sessions were discarded while the application was not running, this will be called shortly after application:didFinishLaunchingWithOptions.
        // Use this method to release any resources that were specific to the discarded scenes, as they will not return.
    }
    
    // MARK: Notifications
    
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        NotificationCenter.default.post(name: NSNotification.didRegisterForRemoteNotificationNotification, object: nil, userInfo: ["data":deviceToken])
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NotificationCenter.default.post(name: NSNotification.didFailToRegisterForRemoteNotification, object: nil, userInfo: ["error":error])
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
//        userController.logout()
    }
}


extension NSNotification {
    public static let didRegisterForRemoteNotificationNotification = NSNotification.Name("didRegisterForRemoteNotificationWithDeviceTokenNotification")
    public static let didFailToRegisterForRemoteNotification = NSNotification.Name("didFailToRegisterForRemoteNotificationsWithErrorNotification")

}

extension AppDelegate {
    
    func bindControllers() {
        
        // The vonage sdk uses push notifications to be informed of incomming calls
        // Here we instruct our dummy push implementation to start acquiring device tokens
        // for later registration with vonage backend.
        self.pushController.initialisePushTokens()

        // Voip push notifications should be forwarded to the vonage client
        pushController.voipPush.sink {
            self.callController.reportVoipPush($0)
        }
        .store(in: &cancellables)
        
        // Integrating applications will most likely already have their user auth flow
        // defined. Typically users authentication will be restored from keychain on app startup.
        // We should aim to provide a vonage bearer token to the sdk client as soon as possible in the app
        // startup flow so we can handle incoming calls
        
        userController.user
            .replaceError(with: nil)
            .sink { (user) in
                // update vonage bearer token
                self.callController.updateSessionToken(user?.token)
            }
            .store(in: &cancellables)
        
        // Once the device has registered for push AND we have an authenticated user
        // register device tokens with vonage
        userController.user
            .replaceError(with: nil)
            .combineLatest(pushController.pushKitToken, pushController.notificationToken)
            .filter { (t1,t2, t3) in t1 != nil && t2 != nil && t3 != nil }
            .sink { token in
                self.callController.registerPushTokens((user:token.2!,voip:token.1!))
            }
            .store(in: &cancellables)
    }
}
