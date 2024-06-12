//
//  LoginViewController.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 25/01/2023.
//

import Foundation
import UIKit
import Combine


class LoginViewModel {
    @Published var user: Result<User,UserControllerErrors>? =  nil
    @Published var error: Error?

    var cancellables = Set<AnyCancellable>()
    var controller:UserController? {
        didSet(value) {
            value != nil ? bind(controller: value!) : nil
        }
    }
    
    func login(displayName: String, phoneNumber: String) {
        loginViaServer(displayName: displayName, phoneNumber: phoneNumber)
    }

    private func loginViaServer(displayName: String, phoneNumber: String) {
        NetworkController()
            .sendGetCredentialRequest(apiType: CodeLoginAPI(body: LoginRequest(displayName: displayName, phoneNumber: phoneNumber)))
            .sink { completion in
                if case .failure(let error) = completion {
                    print(error)
                    self.error = error
                }
            } receiveValue: { (response: TokenResponse) in
                self.controller?.login(loginUser: User(displayName: response.displayName, username: response.username, userId: response.userId, token: response.token))
            }.store(in: &cancellables)
    }

    
    func bind(controller:UserController) {
        controller.user.compactMap{$0}.asResult().map { result in result.map { $0} }
        .assign(to: &self.$user)
    }
}


class LoginViewController: BaseViewController {
    
    var loginLabel: UILabel!
    var displayNameInput: UITextField!
    var phoneNumberInput: UITextField!

    var submitButton: UIButton!
    
    var viewModel: LoginViewModel? {
        didSet(value) {
            if (self.isViewLoaded) { bind()}
        }
    }
    
    var cancels = Set<AnyCancellable>()

    override func loadView() {
        super.loadView()
        view = UIView()
        view.backgroundColor = .white

        loginLabel = UILabel()
        loginLabel.text = "Login"
        loginLabel.font = .systemFont(ofSize: 24)
        
        displayNameInput = UITextField()
        displayNameInput.translatesAutoresizingMaskIntoConstraints = false
        displayNameInput.placeholder = "Name"
        
        phoneNumberInput = UITextField()
        phoneNumberInput.translatesAutoresizingMaskIntoConstraints = false
        phoneNumberInput.placeholder = "6512345678"
        phoneNumberInput.keyboardType = .numberPad
        
        submitButton = UIButton()
        submitButton.setTitle("Sign In", for: .normal)
        submitButton.backgroundColor = UIColor.black
        submitButton.addTarget(self, action: #selector(submitButtonPressed), for: .touchUpInside)
        submitButton.isEnabled = true
        
        let formContainerView = UIStackView()
        formContainerView.translatesAutoresizingMaskIntoConstraints = false
        formContainerView.axis = .vertical
        formContainerView.distribution = .equalSpacing
        formContainerView.alignment = .fill
        formContainerView.spacing = 20;
        formContainerView.setContentHuggingPriority(.defaultLow, for: .vertical)

        formContainerView.addArrangedSubview(loginLabel)
        formContainerView.addArrangedSubview(displayNameInput)
        formContainerView.addArrangedSubview(phoneNumberInput)
        formContainerView.addArrangedSubview(submitButton)
        formContainerView.addArrangedSubview(UIView())

        let formContainerParentView = UIView()
        formContainerParentView.addSubview(formContainerView)

        
        let RootView = UIStackView()
        RootView.translatesAutoresizingMaskIntoConstraints = false
        RootView.axis = .vertical
        RootView.distribution = .fill
        RootView.alignment = .fill
        RootView.addArrangedSubview(formContainerParentView)

        view.addSubview(RootView)
        
        NSLayoutConstraint.activate([
            RootView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 22.5),
            RootView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            RootView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            RootView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.8),
            
            formContainerView.widthAnchor.constraint(equalTo: formContainerParentView.widthAnchor),
            formContainerView.centerXAnchor.constraint(equalTo: formContainerParentView.centerXAnchor),

            //formContainerView.centerYAnchor.constraint(equalTo: formContainerParentView.centerYAnchor),
            
            loginLabel.heightAnchor.constraint(equalToConstant: 45.0),
            displayNameInput.heightAnchor.constraint(equalToConstant: 45.0),
            phoneNumberInput.heightAnchor.constraint(equalToConstant: 45.0),
            submitButton.heightAnchor.constraint(equalToConstant: 45.0),
        ])
        
        viewModel?.$error
            .compactMap { $0?.localizedDescription }
            .receive(on: DispatchQueue.main)
            .assign(to: &($error))
        
        bind()
    }
    
    func bind() {
        
        guard let viewModel else {
            return
        }
    }

    @objc func submitButtonPressed(_ sender:UIButton) {
        viewModel?.login(displayName: displayNameInput.text ?? "", phoneNumber: phoneNumberInput.text ?? "")
    }
}
