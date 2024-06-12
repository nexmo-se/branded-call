//
//  NetworkController.swift
//  VonageSDKClientVOIPExample
//
//  Created by Mehboob Alam on 27.06.23.
//
import Foundation

import Foundation
import Combine

/// API TYPE
protocol ApiType {
    var url: URL {get}
    var method: String {get}
    var headers: [String: String] {get}
    var body: Encodable? {get}
}

extension ApiType {
    var headers: [String: String] {
        [
            "Content-Type": "application/json"
        ]
    }
}

/// LOGIN VIA CODE
struct CodeLoginAPI: ApiType {
    var url: URL = Configuration.getLoginUrl()
    var method: String = "POST"
    var body: Encodable?

    init(body: LoginRequest) {
        self.body = body
    }
}

/// Refreesh Token
struct RefreshTokenAPI: ApiType {
    var url: URL = Configuration.getRefreshTokenUrl()
    var method: String = "POST"
    var body: Encodable?

    init(body: RefreshTokenRequest) {
        self.body = body
    }
}


class NetworkController {
    func sendGetCredentialRequest<type: Decodable>(apiType: any ApiType) -> AnyPublisher<type, Error> {
        var request = URLRequest(url: apiType.url)
        request.httpMethod = apiType.method
        request.allHTTPHeaderFields = apiType.headers
        do {
            if let body = apiType.body {
                request.httpBody = try JSONEncoder().encode(body)
            }
        } catch {
            return Fail(error: error).eraseToAnyPublisher()
        }
        return URLSession
            .shared
            .dataTaskPublisher(for: request)
            .tryMap { data, response -> Data in

                guard let httpResponse = response as? HTTPURLResponse,
                      200..<300 ~= httpResponse.statusCode else {
                    let error = try? JSONSerialization.jsonObject(with: data)
                    print(error ?? "unknown")
                    throw URLError(.badServerResponse)
                }
                if data.isEmpty {
                    return Data()
                }
                return data
            }
            .decode(type: type.self, decoder: JSONDecoder())
            .eraseToAnyPublisher()
        
    }
    
    func sendDeleteUserRequest(body: DeleteUserRequest){
        // Create the request
      var request = URLRequest(url: Configuration.getDeleteUserUrl())
        
        var headers: [String: String] {
            [
                "Content-Type": "application/json"
            ]
        }
      request.allHTTPHeaderFields = headers
       request.httpMethod = "DELETE"
        do {
            request.httpBody = try JSONEncoder().encode(body)
        } catch {
            print(error)
            return
        }
        
       URLSession.shared.dataTask(with: request) { data, response, error in
           guard error == nil else {
               print("Error: error calling DELETE")
               print(error!)
               return
           }

           guard let response = response as? HTTPURLResponse, (200 ..< 299) ~= response.statusCode else {
               print("Error: HTTP request failed")
               return
           }
       }.resume()
    }
    
}

/// NetworkData Models
struct LoginRequest: Encodable {
    let displayName: String
    let phoneNumber: String
}

struct TokenResponse: Decodable {
    let displayName: String
    let username: String
    let userId: String
    let token: String
}

struct RefreshTokenRequest: Encodable {
    let displayName: String
    let phoneNumber: String
}

struct DeleteUserRequest: Encodable {
    let userId: String
}

