//
//  UserController.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 25/01/2023.
//

import Foundation
import Combine
 
typealias UserToken = String

enum UserControllerErrors:Error {
    case InvalidCredentials
    case unknown
}



class UserController: NSObject {
    
    var user =  PassthroughSubject<(User)?,UserControllerErrors>()
    var cancellables = Set<AnyCancellable>()

    func login(loginUser: User) {
        UserDefaults.standard.setValue(loginUser.displayName, forKey: "DISPLAY_NAME")
        UserDefaults.standard.setValue(loginUser.username, forKey: "USER_NAME")
        UserDefaults.standard.setValue(loginUser.userId, forKey: "USER_ID")
        UserDefaults.standard.setValue(loginUser.token, forKey: "TOKEN")
        user.send(loginUser)

    }
    
    func restoreUser(refreshToken: Bool = false) {
        guard let username = UserDefaults.standard.string(forKey: "USER_NAME"),
        let displayName = UserDefaults.standard.string(forKey: "DISPLAY_NAME"),
        let userId = UserDefaults.standard.string(forKey: "USER_ID"),
          let token =  UserDefaults.standard.string(forKey: "TOKEN") else {
              user.send(nil)
            return
          }

        if (!refreshToken) {
            login(loginUser: User(displayName: displayName, username: username, userId: userId, token: token))
          return
        }
        
        // refresh token
        NetworkController()
            .sendGetCredentialRequest(apiType: RefreshTokenAPI(body: RefreshTokenRequest(displayName: displayName, phoneNumber: username)))
            .sink { completion in
                if case .failure(let error) = completion {
                    print(error)
                }
            } receiveValue: { (response: TokenResponse) in
                self.login(loginUser: User(displayName: response.displayName, username: response.username, userId: response.userId, token: response.token))
            }.store(in: &cancellables)

    }
    
    func logout() {
        guard let userId = UserDefaults.standard.string(forKey: "USER_ID"),
          let token =  UserDefaults.standard.string(forKey: "TOKEN") else {
            return
          }
        
        self.user.send(nil)
        
        NetworkController().sendDeleteUserRequest(body: DeleteUserRequest(userId: userId))
        UserDefaults.standard.removeObject(forKey: "DISPLAY_NAME")
        UserDefaults.standard.removeObject(forKey: "USER_NAME")
        UserDefaults.standard.removeObject(forKey: "USER_ID")
        UserDefaults.standard.removeObject(forKey: "TOKEN") 
    }
    
}
