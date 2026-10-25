import Foundation
import AVFoundation

final class SpeechManager: ObservableObject {
    static let shared = SpeechManager()
    private let synthesizer = AVSpeechSynthesizer()

    func speak(_ text: String) {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "de-DE")
        utterance.rate = 0.46
        utterance.pitchMultiplier = 1.0
        synthesizer.speak(utterance)
    }
}
