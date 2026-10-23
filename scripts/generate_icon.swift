import AppKit

let outputDir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "build/AppIcon.iconset"
let fm = FileManager.default
try? fm.createDirectory(atPath: outputDir, withIntermediateDirectories: true)

func renderIcon(size: Int, scale: Int, filename: String) {
    let pixelSize = size * scale
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: pixelSize,
        pixelsHigh: pixelSize,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    )!

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    guard let cg = NSGraphicsContext.current?.cgContext else { return }

    let s = CGFloat(pixelSize)
    let rect = NSRect(x: 0, y: 0, width: s, height: s)
    let inset = s * 0.055
    let cardRect = rect.insetBy(dx: inset, dy: inset)
    let radius = s * 0.22

    // 1. Dark Blood-Obsidian Rounded Squircle Background
    let bgPath = NSBezierPath(roundedRect: cardRect, xRadius: radius, yRadius: radius)
    bgPath.addClip()

    let bgGradient = NSGradient(colors: [
        NSColor(calibratedRed: 0.20, green: 0.02, blue: 0.05, alpha: 1.0),
        NSColor(calibratedRed: 0.08, green: 0.01, blue: 0.02, alpha: 1.0),
        NSColor(calibratedRed: 0.03, green: 0.01, blue: 0.02, alpha: 1.0)
    ])!
    bgGradient.draw(in: bgPath, angle: -90)

    // Crimson radial glow behind the brain
    let glowCenter = NSPoint(x: s * 0.5, y: s * 0.54)
    let glowGrad = NSGradient(colors: [
        NSColor(calibratedRed: 0.85, green: 0.05, blue: 0.12, alpha: 0.55),
        NSColor(calibratedRed: 0.50, green: 0.02, blue: 0.06, alpha: 0.20),
        NSColor(calibratedRed: 0.0, green: 0.0, blue: 0.0, alpha: 0.0)
    ])!
    glowGrad.draw(fromCenter: glowCenter, radius: s * 0.04, toCenter: glowCenter, radius: s * 0.44, options: [])

    // 2. Blood splatters in background
    let splatterPositions: [(CGFloat, CGFloat, CGFloat)] = [
        (0.24, 0.74, 0.022), (0.77, 0.76, 0.018), (0.19, 0.42, 0.015),
        (0.82, 0.46, 0.020), (0.28, 0.26, 0.016), (0.73, 0.25, 0.024),
        (0.64, 0.82, 0.014), (0.35, 0.81, 0.013)
    ]
    NSColor(calibratedRed: 0.75, green: 0.03, blue: 0.09, alpha: 0.75).setFill()
    for (px, py, pr) in splatterPositions {
        let r = s * pr
        let dot = NSBezierPath(ovalIn: NSRect(x: s * px - r, y: s * py - r, width: r * 2, height: r * 2))
        dot.fill()
    }

    // 3. Draw Anatomical Brain Base Shape (Side-view / 3D Cortex Silhouette)
    let brainPath = NSBezierPath()
    // Start at frontal lobe bottom-left
    brainPath.move(to: NSPoint(x: s * 0.25, y: s * 0.46))
    // Frontal lobe curve up
    brainPath.curve(to: NSPoint(x: s * 0.22, y: s * 0.63),
                    controlPoint1: NSPoint(x: s * 0.16, y: s * 0.48),
                    controlPoint2: NSPoint(x: s * 0.16, y: s * 0.58))
    // Superior frontal gyrus
    brainPath.curve(to: NSPoint(x: s * 0.38, y: s * 0.78),
                    controlPoint1: NSPoint(x: s * 0.23, y: s * 0.72),
                    controlPoint2: NSPoint(x: s * 0.30, y: s * 0.78))
    // Parietal lobe top arch
    brainPath.curve(to: NSPoint(x: s * 0.62, y: s * 0.79),
                    controlPoint1: NSPoint(x: s * 0.46, y: s * 0.82),
                    controlPoint2: NSPoint(x: s * 0.55, y: s * 0.82))
    // Occipital lobe back curve
    brainPath.curve(to: NSPoint(x: s * 0.80, y: s * 0.62),
                    controlPoint1: NSPoint(x: s * 0.72, y: s * 0.78),
                    controlPoint2: NSPoint(x: s * 0.80, y: s * 0.71))
    // Lower occipital & cerebellum bulge
    brainPath.curve(to: NSPoint(x: s * 0.73, y: s * 0.41),
                    controlPoint1: NSPoint(x: s * 0.82, y: s * 0.52),
                    controlPoint2: NSPoint(x: s * 0.80, y: s * 0.43))
    // Cerebellum tuck & temporal lobe bottom
    brainPath.curve(to: NSPoint(x: s * 0.48, y: s * 0.39),
                    controlPoint1: NSPoint(x: s * 0.67, y: s * 0.35),
                    controlPoint2: NSPoint(x: s * 0.55, y: s * 0.35))
    // Temporal pole back to frontal base
    brainPath.curve(to: NSPoint(x: s * 0.25, y: s * 0.46),
                    controlPoint1: NSPoint(x: s * 0.38, y: s * 0.38),
                    controlPoint2: NSPoint(x: s * 0.29, y: s * 0.41))
    brainPath.close()

    // Shadow under brain
    cg.saveGState()
    cg.setShadow(offset: CGSize(width: 0, height: -s * 0.02), blur: s * 0.05, color: NSColor.black.cgColor)
    let brainFillGrad = NSGradient(colors: [
        NSColor(calibratedRed: 0.95, green: 0.32, blue: 0.38, alpha: 1.0),
        NSColor(calibratedRed: 0.78, green: 0.12, blue: 0.20, alpha: 1.0),
        NSColor(calibratedRed: 0.48, green: 0.03, blue: 0.08, alpha: 1.0)
    ])!
    brainFillGrad.draw(in: brainPath, angle: -75)
    cg.restoreGState()

    // Crimson blood pool / stain overlay on lower cortex
    NSGraphicsContext.saveGraphicsState()
    brainPath.addClip()
    let bloodOverlayGrad = NSGradient(colors: [
        NSColor(calibratedRed: 0.82, green: 0.0, blue: 0.07, alpha: 0.0),
        NSColor(calibratedRed: 0.75, green: 0.0, blue: 0.06, alpha: 0.65),
        NSColor(calibratedRed: 0.42, green: 0.0, blue: 0.03, alpha: 0.92)
    ])!
    bloodOverlayGrad.draw(in: NSRect(x: s * 0.15, y: s * 0.34, width: s * 0.70, height: s * 0.30), angle: -90)

    // 4. Draw Cerebral Sulci / Gyri Folds (Dark Crimson / Anatomical Folds)
    let sulci = NSBezierPath()
    sulci.lineWidth = s * 0.020
    sulci.lineCapStyle = .round
    sulci.lineJoinStyle = .round

    // Lateral sulcus (Sylvian fissure)
    sulci.move(to: NSPoint(x: s * 0.32, y: s * 0.47))
    sulci.curve(to: NSPoint(x: s * 0.60, y: s * 0.56),
                controlPoint1: NSPoint(x: s * 0.42, y: s * 0.48),
                controlPoint2: NSPoint(x: s * 0.50, y: s * 0.55))

    // Central sulcus
    sulci.move(to: NSPoint(x: s * 0.49, y: s * 0.79))
    sulci.curve(to: NSPoint(x: s * 0.45, y: s * 0.53),
                controlPoint1: NSPoint(x: s * 0.44, y: s * 0.70),
                controlPoint2: NSPoint(x: s * 0.49, y: s * 0.61))

    // Frontal gyri folds
    sulci.move(to: NSPoint(x: s * 0.25, y: s * 0.59))
    sulci.curve(to: NSPoint(x: s * 0.41, y: s * 0.65),
                controlPoint1: NSPoint(x: s * 0.30, y: s * 0.64),
                controlPoint2: NSPoint(x: s * 0.36, y: s * 0.60))

    sulci.move(to: NSPoint(x: s * 0.33, y: s * 0.73))
    sulci.curve(to: NSPoint(x: s * 0.44, y: s * 0.68),
                controlPoint1: NSPoint(x: s * 0.37, y: s * 0.70),
                controlPoint2: NSPoint(x: s * 0.41, y: s * 0.72))

    // Parietal-occipital folds
    sulci.move(to: NSPoint(x: s * 0.60, y: s * 0.77))
    sulci.curve(to: NSPoint(x: s * 0.57, y: s * 0.58),
                controlPoint1: NSPoint(x: s * 0.56, y: s * 0.70),
                controlPoint2: NSPoint(x: s * 0.62, y: s * 0.64))

    sulci.move(to: NSPoint(x: s * 0.64, y: s * 0.67))
    sulci.curve(to: NSPoint(x: s * 0.76, y: s * 0.57),
                controlPoint1: NSPoint(x: s * 0.69, y: s * 0.66),
                controlPoint2: NSPoint(x: s * 0.73, y: s * 0.62))

    // Temporal lobe folds
    sulci.move(to: NSPoint(x: s * 0.40, y: s * 0.43))
    sulci.curve(to: NSPoint(x: s * 0.66, y: s * 0.47),
                controlPoint1: NSPoint(x: s * 0.49, y: s * 0.43),
                controlPoint2: NSPoint(x: s * 0.58, y: s * 0.49))

    NSColor(calibratedRed: 0.32, green: 0.01, blue: 0.05, alpha: 0.88).setStroke()
    sulci.stroke()

    // Glowing blood vessels inside sulci
    let veins = sulci.copy() as! NSBezierPath
    veins.lineWidth = s * 0.007
    NSColor(calibratedRed: 1.0, green: 0.12, blue: 0.18, alpha: 0.85).setStroke()
    veins.stroke()
    NSGraphicsContext.restoreGraphicsState()

    // Outer brain rim highlight
    brainPath.lineWidth = s * 0.012
    NSColor(calibratedRed: 0.90, green: 0.08, blue: 0.15, alpha: 0.9).setStroke()
    brainPath.stroke()

    // 5. Draw Dripping Blood Streams & Drops from the Brain
    let dripStreams: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
        // (x, topY, bottomY, width)
        (0.31, 0.43, 0.22, 0.032),
        (0.44, 0.40, 0.15, 0.038),
        (0.56, 0.39, 0.25, 0.028),
        (0.68, 0.41, 0.19, 0.034)
    ]

    let bloodGrad = NSGradient(colors: [
        NSColor(calibratedRed: 0.92, green: 0.02, blue: 0.08, alpha: 1.0),
        NSColor(calibratedRed: 0.55, green: 0.0, blue: 0.04, alpha: 1.0)
    ])!

    for (dx, topY, botY, w) in dripStreams {
        let cx = s * dx
        let sy = s * topY
        let ey = s * botY
        let r = s * w

        // Vertical stem + teardrop bulb at bottom
        let drip = NSBezierPath()
        drip.move(to: NSPoint(x: cx - r * 0.45, y: sy))
        drip.line(to: NSPoint(x: cx - r * 0.35, y: ey + r * 1.1))
        drip.curve(to: NSPoint(x: cx, y: ey - r),
                   controlPoint1: NSPoint(x: cx - r * 1.1, y: ey + r * 0.2),
                   controlPoint2: NSPoint(x: cx - r * 0.8, y: ey - r))
        drip.curve(to: NSPoint(x: cx + r * 0.35, y: ey + r * 1.1),
                   controlPoint1: NSPoint(x: cx + r * 0.8, y: ey - r),
                   controlPoint2: NSPoint(x: cx + r * 1.1, y: ey + r * 0.2))
        drip.line(to: NSPoint(x: cx + r * 0.45, y: sy))
        drip.close()

        bloodGrad.draw(in: drip, angle: -90)

        // Specular highlight on blood drop
        let shine = NSBezierPath(ovalIn: NSRect(x: cx - r * 0.42, y: ey - r * 0.35, width: r * 0.32, height: r * 0.55))
        NSColor(calibratedRed: 1.0, green: 0.65, blue: 0.68, alpha: 0.65).setFill()
        shine.fill()
    }

    // Detached falling blood droplets below
    let droplets: [(CGFloat, CGFloat, CGFloat)] = [
        (0.31, 0.12, 0.020),
        (0.56, 0.14, 0.022)
    ]
    for (dx, dy, dr) in droplets {
        let cx = s * dx
        let cy = s * dy
        let r = s * dr
        let drop = NSBezierPath()
        drop.move(to: NSPoint(x: cx, y: cy + r * 1.4))
        drop.curve(to: NSPoint(x: cx, y: cy - r),
                   controlPoint1: NSPoint(x: cx - r * 1.2, y: cy + r * 0.2),
                   controlPoint2: NSPoint(x: cx - r * 0.9, y: cy - r))
        drop.curve(to: NSPoint(x: cx, y: cy + r * 1.4),
                   controlPoint1: NSPoint(x: cx + r * 0.9, y: cy - r),
                   controlPoint2: NSPoint(x: cx + r * 1.2, y: cy + r * 0.2))
        drop.close()
        bloodGrad.draw(in: drop, angle: -90)
    }

    // Inner border ring
    let borderPath = NSBezierPath(roundedRect: cardRect.insetBy(dx: s * 0.008, dy: s * 0.008), xRadius: radius, yRadius: radius)
    borderPath.lineWidth = s * 0.014
    NSColor(calibratedRed: 0.85, green: 0.05, blue: 0.12, alpha: 0.45).setStroke()
    borderPath.stroke()

    NSGraphicsContext.restoreGraphicsState()

    if let pngData = rep.representation(using: .png, properties: [:]) {
        let fileURL = URL(fileURLWithPath: outputDir).appendingPathComponent(filename)
        try? pngData.write(to: fileURL)
    }
}

let sizes = [16, 32, 128, 256, 512]
for sz in sizes {
    renderIcon(size: sz, scale: 1, filename: "icon_\(sz)x\(sz).png")
    renderIcon(size: sz, scale: 2, filename: "icon_\(sz)x\(sz)@2x.png")
}
