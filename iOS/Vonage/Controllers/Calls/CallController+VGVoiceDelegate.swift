//
//  CallController+VGVoiceDelegate.swift
//  VonageSDKClientVOIPExample
//
//  Created by Ashley Arthur on 12/02/2023.
//

import Foundation
import VonageClientSDKVoice
import CallKit

typealias CallUpdate = (call:UUID, leg:UUID, status:String)


extension VonageCallController: VGVoiceClientDelegate {
    // MARK: VGVoiceClientDelegate Sessions

    func clientWillReconnect(_ client: VGBaseClient) {
        vonageWillReconnect.send(())
    }
    
    func clientDidReconnect(_ client: VGBaseClient) {
        vonageDidReconnect.send(())
    }
    
    func client(_ client: VGBaseClient, didReceiveSessionErrorWith reason: VGSessionErrorReason) {
        vonageSessionError.send(reason)
    }
    
    // MARK: VGVoiceClientDelegate Invites
    
    func voiceClient(_ client: VGVoiceClient, didReceiveInviteForCall callId: String, from caller: String, with type: VGVoiceChannelType) {
        let uuid = UUID(uuidString: callId)!
        self.vonageCalls.send(Call.inbound(id: uuid, from: caller, status: .ringing))
    }
    
    func voiceClient(_ client: VGVoiceClient, didReceiveInviteCancelForCall callId: VGCallId, with reason: VGVoiceInviteCancelReason) {
        let uuid = UUID(uuidString: callId)!
        var cxreason: CXCallEndedReason = .failed
        
        switch (reason){
        case .remoteTimeout: cxreason = .unanswered
        case .answeredElsewhere: cxreason = .answeredElsewhere
        case .rejectedElsewhere: cxreason = .declinedElsewhere
        case .remoteCancel: cxreason = .remoteEnded
        case .unknown: fatalError()
            
        @unknown default:
            fatalError()
        }
        self.vonageCallUpdates.send((uuid, CallStatus.completed(remote: true, reason: cxreason)))
    }
        
    
    func voiceClient(_ client: VGVoiceClient, didReceiveHangupForCall callId: VGCallId, withQuality callQuality: VGRTCQuality, reason: VGHangupReason) {
        let uuid = UUID(uuidString: callId)!
        var cxreason: CXCallEndedReason = .failed

        switch (reason){
        case .mediaTimeout: cxreason = .unanswered
        case .remoteReject: cxreason = .declinedElsewhere
        case .localHangup: cxreason = .remoteEnded
        case .remoteHangup: cxreason = .remoteEnded
        case .unknown: cxreason = .unanswered
        case .remoteNoAnswerTimeout: cxreason = .unanswered
        @unknown default:
            fatalError()
        }
        self.vonageCallUpdates.send((uuid, CallStatus.completed(remote: true, reason: cxreason)))
    }
    
    
    // MARK: VGVoiceClientDelegate LegStatus
    
    func voiceClient(_ client: VGVoiceClient, didReceiveLegStatusUpdateForCall callId: VGCallId, withLegId legId: String, andStatus status: VGLegStatus) {
        if (status == .answered ) {
            let uuid = UUID(uuidString: callId)!
            vonageCallUpdates.send((uuid, CallStatus.answered(time: Date.now)))
        }
    }
    
    func voiceClient(_ client: VGVoiceClient, didReceiveCallTransferForCall callId: VGCallId, withConversationId conversationId: String) {
        // this will only be triggered for our own legs
        let uuid = UUID(uuidString: callId)!
        vonageCallUpdates.send((uuid, CallStatus.answered(time: Date.now))) // report to Call Kit
    }

}
