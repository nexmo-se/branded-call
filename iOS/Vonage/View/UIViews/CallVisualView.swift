//
//  CallVisualView.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 14/04/2023.
//

import Foundation
import UIKit


enum CallVisualStatus {
    case ringing
    case answered
    case completed
}

class CallVisualView: UIButton {
    
    private var vonageImageView = UIImageView(image: UIImage(named: "vonage-image.png")!)
    private var secureCallStack: UIStackView!

    override init(frame: CGRect) {
      super.init(frame: frame)
      setupView()
    }

    required init?(coder aDecoder: NSCoder) {
      super.init(coder: aDecoder)
      setupView()
    }
    
    private func setupView() {
        self.translatesAutoresizingMaskIntoConstraints = false

        vonageImageView.frame = CGRect(x: -130, y: -60, width: 300, height: 120)
        vonageImageView.contentMode = .scaleAspectFit
        
        addSubview(vonageImageView)
        setupLayout()
    }
    
    private func setupLayout() {
        self.setNeedsUpdateConstraints()
    }
    
    override class var requiresConstraintBasedLayout: Bool {
        return true
    }
}


fileprivate extension CallVisualView{
    
    static let RingingAnimation: CAAnimation = { () -> CAAnimation in
        var anim = [CABasicAnimation]()
        let transformAnim = CABasicAnimation(keyPath: "transform.scale")
        transformAnim.duration = 2.0
        transformAnim.repeatCount = 200
        transformAnim.fromValue = 0.0
        transformAnim.toValue = 5.0
        transformAnim.timingFunction = CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeOut)
        anim.append(transformAnim)
        
        let alphaAnim = CABasicAnimation(keyPath: #keyPath(CALayer.opacity))
        alphaAnim.duration = 2.0
        alphaAnim.repeatCount = 200
        alphaAnim.fromValue = [1.0]
        alphaAnim.toValue = [0.0]
        alphaAnim.fillMode = .forwards
        transformAnim.timingFunction = CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeOut)
        
        anim.append(alphaAnim)
        
        let group = CAAnimationGroup()
        //        group.timingFunction = CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeOut)
        group.animations = anim
        group.duration = 10.0
        group.repeatCount = 200
        
        return group
    }()
    
    
    static let answerAnimation: CAAnimation = { () -> CAAnimation in
        var anim = [CAAnimation]()
        let transformAnim = CAKeyframeAnimation(keyPath: "transform.scale")
        transformAnim.duration = 2
        transformAnim.repeatCount = 200
        transformAnim.values = [1.0, 1.1, 1.0]
        transformAnim.keyTimes = [0, 0.333, 1]
        transformAnim.timingFunctions = [
            CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeInEaseOut),
            CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeInEaseOut)
        ]
        anim.append(transformAnim)
        
        let group = CAAnimationGroup()
        group.animations = anim
        group.duration = 4
        group.repeatCount = 200
        
        return group
    }()
    
    
    static let RejectedAnimation: CAAnimation = { () -> CAAnimation in
        var anim = [CABasicAnimation]()
        
        let alphaAnim = CABasicAnimation(keyPath: #keyPath(CALayer.opacity))
        alphaAnim.duration = 0.5
        alphaAnim.repeatCount = 4
        alphaAnim.fromValue = [1.0]
        alphaAnim.toValue = [0.0]
        alphaAnim.timingFunction = CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeOut)
        anim.append(alphaAnim)
        
        let group = CAAnimationGroup()
        group.animations = anim
        group.duration = 2
        group.repeatCount = 1
        
        return group
    }()
}
