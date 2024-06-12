//
//  DialerView.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 14/02/2023.
//

import Foundation
import UIKit


class DialerView: UIView {
    
    typealias onClickHandler = (String) -> Void
    
    private let inner = {
        let stackView = UIStackView()
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.distribution = .fill
        stackView.alignment = .fill
        stackView.spacing = 40;
        return stackView
    }()
    private var buttons: [UIButton] = []
    
    
    public var onClick:onClickHandler?
    
    override init(frame: CGRect) {
      super.init(frame: frame)
      setupView()
    }
    
    required init?(coder aDecoder: NSCoder) {
      super.init(coder: aDecoder)
      setupView()
    }
    
    private func setupView() {
        self.addSubview(inner)
        
        let buttonValues = [
            [ "1", "2", "3"],
            [ "4", "5", "6"],
            [ "7", "8", "9"],
            [ "*", "0", "#"]
        ]
        
        let rows = buttonValues.map { row in
            return row.map {
                let button = CircularButton()
                button.setTitle($0, for:.normal)
                button.backgroundColor = UIColor.black
                button.addTarget(self, action: #selector(executeOnclick), for: .touchUpInside)
                return button
            }
        }
        .map { _buttons in
            return (UIStackView(), _buttons)
        }
        
        buttons = rows.flatMap { (_,b) in b }
        
        rows.forEach { (container,_buttons) in
            inner.addArrangedSubview(container)
            container.translatesAutoresizingMaskIntoConstraints = false
            container.axis = .horizontal
            container.distribution = .equalSpacing
            container.alignment = .fill
            container.spacing = 20
            
            _buttons.forEach { b in
                container.addArrangedSubview(b)
            }
        }
        
        setupLayout()
    }
    
    @objc private func executeOnclick(_ b:UIButton) {
        guard let callback = self.onClick else {
            return
        }
        callback(b.titleLabel?.text ?? "")
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
    }
    
    private func setupLayout() {
        self.addConstraints([
            inner.leftAnchor.constraint(equalTo: self.leftAnchor),
            inner.rightAnchor.constraint(equalTo: self.rightAnchor),
            inner.topAnchor.constraint(equalTo: self.topAnchor),
            inner.bottomAnchor.constraint(equalTo: self.bottomAnchor)
        ])
        
        self.buttons.forEach {
            self.addConstraints([
                $0.heightAnchor.constraint(equalToConstant: 75.0),
                $0.widthAnchor.constraint(equalTo: $0.heightAnchor)
            ])
        }

        self.setNeedsUpdateConstraints()
    }
    
    override class var requiresConstraintBasedLayout: Bool {
        return true
    }
}
