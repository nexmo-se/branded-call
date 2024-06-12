//
//  CircularRingView.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 14/02/2023.
//

import Foundation
import UIKit

class UICustomRingView: UIView {
    
    private let inner: UIView = UIView()
    var ringWidth: CGFloat = 0.85
    var ringSize: CGFloat = 250
    
    override init(frame: CGRect) {
      super.init(frame: frame)
      setupView()
    }
    
    required init?(coder aDecoder: NSCoder) {
      super.init(coder: aDecoder)
      setupView()
    }
    
    private func setupView() {
        inner.backgroundColor = .systemBackground
        self.translatesAutoresizingMaskIntoConstraints = false
        inner.translatesAutoresizingMaskIntoConstraints = false
        addSubview(inner)
        setupLayout()
    }
    
    override var intrinsicContentSize: CGSize {
      return CGSize(width: ringSize, height: ringSize)
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.cornerRadius = bounds.size.width * 0.5
        inner.layer.cornerRadius = bounds.size.width * ringWidth * 0.5
    }
    
    private func setupLayout() {
        self.addConstraints([
            inner.heightAnchor.constraint(equalTo: self.heightAnchor, multiplier: ringWidth),
            inner.widthAnchor.constraint(equalTo: self.widthAnchor, multiplier: ringWidth),

            inner.centerXAnchor.constraint(equalTo: self.centerXAnchor),
            inner.centerYAnchor.constraint(equalTo: self.centerYAnchor),
        ])
        self.setNeedsUpdateConstraints()
    }
    
    override class var requiresConstraintBasedLayout: Bool {
        return true
    }
}
