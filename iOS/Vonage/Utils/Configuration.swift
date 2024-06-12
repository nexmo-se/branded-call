//
//  Configuration.swift
//  VonageSDKClientVOIPExample
//
//  Created by Mehboob Alam on 27.06.23.
//
import Foundation

private var backendServer = ""

enum Configuration {

    static func getLoginUrl() -> URL {
        let urlString = "\(backendServer)/getCredential"
        guard let url = URL(string: urlString) else {
            fatalError("Missing Login URL");
        }
        return url
    }

    static func getRefreshTokenUrl() -> URL {
        let urlString = "\(backendServer)/getCredential"
        guard let url = URL(string: urlString) else {
            fatalError("Missing Refresh Token URL");
        }
        return url
    }
    
    static func getDeleteUserUrl() -> URL {
        let urlString = "\(backendServer)/deleteUser"
        guard let url = URL(string: urlString) else {
            fatalError("Missing Delete User URL");
        }
        return url
    }
    
    static let defaultToken : String = ""
}
