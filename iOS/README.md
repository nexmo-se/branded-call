# Vonage VOIP Application Sample

An iOS application powered by the Vonage Voice API to make and receive VOIP Calls.

## Installation

Note: A minimum version of Xcode 14.x is required to build and run.

To install, first make sure you have [CocoaPods](https://cocoapods.org) installed on your system. Then, follow these steps:

## iOS
1. Clone this repository
1. Go to `iOS` folder
1. Run `pod install` to install the dependencies
1. Open your project in Xcode using the .xcworkspace file
1. Go to Uitls/Configuration.swift File and paste your backend server url to `backendServer`.
1. Connect your device and run the project. \
Note: Run it on real device, as the simulator might not works for callkit and/or voip push

## Setup Push Notification
1. Generate Push Cert: [Link](https://developer.vonage.com/en/vonage-client-sdk/set-up-push-notifications/ios#generating-a-push-certificate)
1. Upload your Push Cert: [Link](https://developer.vonage.com/en/vonage-client-sdk/set-up-push-notifications/ios#upload-your-certificate)

