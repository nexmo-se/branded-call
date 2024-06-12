//
//  BaseViewController.swift
//  VonageSDKClientVOIPExample
//
//  Created by Mehboob Alam on 28.06.23.
//
import UIKit
import Combine

class BaseViewController: UIViewController {
    @Published var error: String?
    var cancellables: Set<AnyCancellable> = []

    override func viewDidLoad() {
        super.viewDidLoad()
        $error
            .compactMap { $0 }
            .sink { error in
                self.showOkayAlert(message: error)
            }.store(in: &cancellables)
    }
    func showOkayAlert(title: String = "Alert!!", message: String) {
        let alertController = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alertController.addAction(UIAlertAction(title: "Okay", style: .default))
        self.present(alertController, animated: true)
    }
}
