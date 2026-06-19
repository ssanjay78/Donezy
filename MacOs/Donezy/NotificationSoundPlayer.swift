import Foundation
import AVFoundation

final class NotificationSoundPlayer {
    static let shared = NotificationSoundPlayer()
    
    private var audioPlayer: AVAudioPlayer?
    private var timer: Timer?
    
    private init() {}
    
    func play(soundName: String?, durationSeconds: Int) {
        stop()
        
        let fileURL: URL
        if let soundName = soundName, !soundName.isEmpty {
            // Library/Sounds is the standard folder for local notification custom sounds
            let libraryDir = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first!
            let soundsDir = libraryDir.appendingPathComponent("Sounds")
            let customSoundURL = soundsDir.appendingPathComponent(soundName)
            
            if FileManager.default.fileExists(atPath: customSoundURL.path) {
                fileURL = customSoundURL
            } else if let bundleURL = Bundle.main.url(forResource: soundName, withExtension: nil) {
                fileURL = bundleURL
            } else {
                playSystemSound()
                return
            }
        } else {
            playSystemSound()
            return
        }
        
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            
            audioPlayer = try AVAudioPlayer(contentsOf: fileURL)
            audioPlayer?.numberOfLoops = -1
            audioPlayer?.play()
            
            DispatchQueue.main.async { [weak self] in
                self?.timer = Timer.scheduledTimer(withTimeInterval: Double(durationSeconds), repeats: false) { _ in
                    self?.stop()
                }
            }
        } catch {
            print("Failed to play custom sound: \(error.localizedDescription)")
            playSystemSound()
        }
    }
    
    private func playSystemSound() {
        AudioServicesPlaySystemSound(1007) // Standard alert system sound ID
    }
    
    func stop() {
        timer?.invalidate()
        timer = nil
        
        audioPlayer?.stop()
        audioPlayer = nil
        
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
