//
//  ActiveCallViewController.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 29/01/2023.
//

import Foundation
import UIKit
import Combine
import CallKit


class ActiveCallViewModel: ObservableObject {
    @Published var call: Call
    
    var currentTimerInSeconds = -1
    var timer = Timer.publish(every: 1, on: .main, in: .default).autoconnect()
    
    var controller: CallController?
    
    private var cancellables = Set<AnyCancellable>()
    
    init(call:Call) {
        self.call = call
    }
    
    func modifyCall(action: CXAction) {
        controller?.reportCXAction(action)
    }
}

class ActiveCallViewController: UIViewController {
    
    let iconConfig = UIImage.SymbolConfiguration(pointSize: 40, weight: .regular, scale: .medium)
    var answerIcon: UIImage!
    var endCallIcon: UIImage!
    var muteCallIcon: UIImage!
    var unMuteCallIcon: UIImage!
    
    var timerLabel: UILabel!
    var calleeLabel: UILabel!
    var callVisual: CallVisualView!

    var answerButton: UIButton!
    var rejectButton: UIButton!
    var hangupButton: UIButton!
    var muteButton: UIButton!
    
    var inboundCallControls: UIView!
    var activeCallControls: UIView!
    var callControlRoot: UIStackView!
        
    var cancels = Set<AnyCancellable>()
    var timerCancel:AnyCancellable?

    var viewModel:ActiveCallViewModel? {
        didSet(value) {
            if (self.isViewLoaded) { bind()}
        }
    }
    
    func isBound() -> Bool {
        return !cancels.isEmpty
    }
    
    func bind() {
        guard let viewModel else {
            return
        }
        _ = cancels.map { $0.cancel() }

        viewModel.$call.first()
            .map { (call) -> String in
                if case let .outbound(_,to,_) = call { return to }
                if case let .inbound(_,from,_) = call { return from }
                return ""
            }
            .receive(on: RunLoop.main)
            .sink(receiveValue: { (s:String) in
                self.calleeLabel.text = s
            })
            .store(in: &cancels)
        
        viewModel.$call
            .receive(on: RunLoop.main)
            .sink(receiveValue: { call in
                switch (call) {
                case .inbound(_,_, let status):
                    switch(status) {
                    case .ringing:
                        self.activeCallControls.removeFromSuperview()
                        self.callControlRoot.addArrangedSubview(self.inboundCallControls)
                    case .answered:
                        self.inboundCallControls.removeFromSuperview()
                        self.callControlRoot.addArrangedSubview(self.activeCallControls)
                    default:
                        return
                    }
                case .outbound:
                    self.inboundCallControls.removeFromSuperview()
                    self.callControlRoot.addArrangedSubview(self.activeCallControls)
                }
            })
            .store(in: &cancels)

        
        viewModel.$call
            .receive(on: RunLoop.main)
            .sink { call in
                switch(call.status) {
                case .ringing:
                    print("ringing")
                case .answered(let time):
                    // start timer
                    let timeDiff = Int(time.distance(to: Date.now))
                    self.viewModel!.currentTimerInSeconds = timeDiff
                case .completed:
                    self.eventuallyDismiss()
                }
            }
            .store(in: &cancels)
        
        viewModel.controller?.isMuted
            .receive(on: RunLoop.main)
            .sink(receiveValue: { isMuted in
                self.updateMuteIcon(isMuted)
            })
            .store(in: &cancels)
            
        
        timerCancel = viewModel.timer
            .receive(on: RunLoop.main)
            .sink { _ in
                if let currentTimeInSeconds = self.viewModel?.currentTimerInSeconds {
                    if currentTimeInSeconds > -1  {
                            self.viewModel?.currentTimerInSeconds += 1
                            self.timerLabel.text = "Call duration - " + self.convertSecondsToTime(timeInseconds: currentTimeInSeconds + 1)
                    }
                }
             
            }

    }

    func eventuallyDismiss() {
        Timer.publish(every: 1.5, on: RunLoop.main, in: .default).autoconnect().first().sink {  _ in
            if self.navigationController?.topViewController == self{
                self.navigationController?.popViewController(animated: true)
            }
        }.store(in: &self.cancels)
        timerCancel?.cancel()
    }
    
    func convertSecondsToTime(timeInseconds: Int) -> String {
        let minutes = timeInseconds / 60
        let seconds = timeInseconds % 60
        
        return String(format: "%02i:%02i", minutes, seconds)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        if (!self.isBound()){
            bind()
        }
    }
    
    override func loadView() {
        super.loadView()
        view = UIView()
        view.backgroundColor = .systemBackground
        
        answerIcon = UIImage(systemName: "phone.fill", withConfiguration: iconConfig)
        endCallIcon = UIImage(systemName: "phone.down.fill", withConfiguration: iconConfig)
        muteCallIcon = UIImage(systemName: "mic.slash.fill", withConfiguration: iconConfig)
        unMuteCallIcon = UIImage(systemName: "mic.fill", withConfiguration: iconConfig)
        
        timerLabel = UILabel()
        timerLabel.textAlignment = .center
        timerLabel.font = UIFont.preferredFont(forTextStyle: .title3)
        
        calleeLabel = UILabel()
        calleeLabel.textAlignment = .center
        calleeLabel.font = UIFont.preferredFont(forTextStyle: .title1)
        calleeLabel.lineBreakMode = .byWordWrapping
        calleeLabel.numberOfLines = 0
        
        let labelConstriants = [
            calleeLabel.heightAnchor.constraint(equalToConstant: 140)
        ]
        
        callVisual = CallVisualView()
        
        answerButton = CircularButton()
        answerButton.translatesAutoresizingMaskIntoConstraints = false
        answerButton.setImage(answerIcon, for: .normal)
        answerButton.tintColor = .white
        answerButton.backgroundColor = .green
        answerButton.addTarget(self, action: #selector(answerButtonPressed), for: .touchUpInside)
        
        rejectButton = CircularButton()
        rejectButton.translatesAutoresizingMaskIntoConstraints = false
        rejectButton.setImage(endCallIcon, for: .normal)
        rejectButton.tintColor = .white
        rejectButton.backgroundColor = .systemRed
        rejectButton.addTarget(self, action: #selector(rejectedButtonPressed), for: .touchUpInside)
        
        hangupButton = CircularButton()
        hangupButton.setContentCompressionResistancePriority(.defaultHigh, for: .horizontal)
        hangupButton.setContentHuggingPriority(.defaultHigh, for: .horizontal)
        hangupButton.translatesAutoresizingMaskIntoConstraints = false
        hangupButton.setImage(endCallIcon, for: .normal)
        hangupButton.tintColor = .white
        hangupButton.backgroundColor = .systemRed
        hangupButton.addTarget(self, action: #selector(hangupButtonPressed), for: .touchUpInside)
        
        muteButton = CircularButton()
        muteButton.backgroundColor = UIColor.systemBlue
        muteButton.translatesAutoresizingMaskIntoConstraints = false
        muteButton.tintColor = .white
        muteButton.addTarget(self, action: #selector(muteButtonPressed), for: .touchUpInside)
        
        let inboundCallControlStack = UIStackView()
        inboundCallControls = inboundCallControlStack
        inboundCallControls.translatesAutoresizingMaskIntoConstraints = false
        inboundCallControlStack.axis = .horizontal
        inboundCallControlStack.distribution = .equalCentering
        inboundCallControlStack.alignment = .center
        inboundCallControlStack.addArrangedSubview(answerButton)
        inboundCallControlStack.addArrangedSubview(rejectButton)

        let activeCallControlStack = UIStackView()
        activeCallControls = activeCallControlStack
        activeCallControls.translatesAutoresizingMaskIntoConstraints = false
        activeCallControlStack.axis = .horizontal
        activeCallControlStack.distribution = .equalCentering
        activeCallControlStack.alignment = .center
        activeCallControlStack.addArrangedSubview(muteButton)
        activeCallControlStack.addArrangedSubview(hangupButton)
        
        let callControlRoot = UIStackView()
        self.callControlRoot = callControlRoot
        callControlRoot.translatesAutoresizingMaskIntoConstraints = false
        callControlRoot.axis = .vertical
        callControlRoot.distribution = .equalCentering
        callControlRoot.alignment = .fill


        let callControlButtonSize = 75.0
        let callControlConstraints = [
            hangupButton.heightAnchor.constraint(equalToConstant: callControlButtonSize),
            hangupButton.widthAnchor.constraint(equalToConstant: callControlButtonSize),
            muteButton.heightAnchor.constraint(equalToConstant: callControlButtonSize),
            muteButton.widthAnchor.constraint(equalToConstant: callControlButtonSize),
            answerButton.heightAnchor.constraint(equalToConstant: callControlButtonSize),
            answerButton.widthAnchor.constraint(equalToConstant: callControlButtonSize),
            rejectButton.heightAnchor.constraint(equalToConstant: callControlButtonSize),
            rejectButton.widthAnchor.constraint(equalToConstant: callControlButtonSize),
            
            callControlRoot.heightAnchor.constraint(greaterThanOrEqualToConstant: callControlButtonSize),
        ]

        let stackView = UIStackView()
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.distribution = .fillProportionally
        stackView.alignment = .fill
        stackView.addArrangedSubview(timerLabel)
        stackView.addArrangedSubview(calleeLabel)
        view.addSubview(stackView)
        view.addSubview(callVisual)
        view.addSubview(callControlRoot)
        
        NSLayoutConstraint.activate(labelConstriants + callControlConstraints + [
            stackView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stackView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.75),
            stackView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 100),
            callVisual.topAnchor.constraint(equalTo: calleeLabel.bottomAnchor, constant: 82),
            callVisual.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            callControlRoot.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            callControlRoot.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.75),
            callControlRoot.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -60)
        ])
        
    }
    
    private func updateMuteIcon(_ isMuted: Bool) {
        if (isMuted) {
            muteButton.setImage(muteCallIcon, for: .normal)
        } else {
            muteButton.setImage(unMuteCallIcon, for: .normal)
        }
    }
    
    @objc func hangupButtonPressed(_ sender:UIButton) {
        self.hangupButton.layer.add(ActiveCallViewController.ButtonPressedAnimation, forKey: "press")
        guard let call = viewModel?.call else {
            return
        }
        viewModel?.modifyCall(action: CXEndCallAction(call: call.id))
    }
    
    @objc func muteButtonPressed(_ sender:UIButton) {
        self.muteButton.layer.add(ActiveCallViewController.ButtonPressedAnimation, forKey: "press")
        guard let call = viewModel?.call, let isMuted =  viewModel?.controller?.isMuted.value else {
            return
        }
        
        viewModel?.modifyCall(action: CXSetMutedCallAction(call: call.id, muted: !isMuted))
    }
    
    @objc func answerButtonPressed(_ sender:UIButton) {
        self.answerButton.layer.add(ActiveCallViewController.ButtonPressedAnimation, forKey: "press")
        guard let call = viewModel?.call else {
            return
        }
        viewModel?.modifyCall(action: CXAnswerCallAction(call: call.id))

    }
    
    @objc func rejectedButtonPressed(_ sender:UIButton) {
        guard let call = viewModel?.call else {
            return
        }
        self.rejectButton.layer.add(ActiveCallViewController.ButtonPressedAnimation, forKey: "press")
        viewModel?.modifyCall(action: CXEndCallAction(call: call.id))
    }
}

fileprivate extension ActiveCallViewController{
    
    static let ButtonPressedAnimation: CAAnimation = { () -> CAAnimation in
        var anim = [CAAnimation]()

        let transformAnim = CAKeyframeAnimation(keyPath: "transform.scale")
        transformAnim.duration = 0.2
        transformAnim.repeatCount = 1
        transformAnim.values = [1.0, 1.05, 1.0]
        transformAnim.keyTimes = [0, 0.333, 1]
        transformAnim.timingFunctions = [
            CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeOut),
            CAMediaTimingFunction(name: CAMediaTimingFunctionName.easeOut)
        ]
        anim.append(transformAnim)
        
        let group = CAAnimationGroup()
        group.animations = anim
        group.duration = 0.5
        group.repeatCount = 1
        
        return group
    }()
}
