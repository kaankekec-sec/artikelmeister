import AppKit
import Foundation

let outputDir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "build/AppIcon.iconset"
let fm = FileManager.default
try? fm.createDirectory(atPath: outputDir, withIntermediateDirectories: true)

// Locate shared/logo/logo_source.png relative to current working directory or script path
let candidatePaths = [
    "../shared/logo/logo_source.png",
    "shared/logo/logo_source.png",
    "/Users/kaankekec/Documents/antigravity/adventurous-hypatia/shared/logo/logo_source.png"
]

guard let sourcePath = candidatePaths.first(where: { fm.fileExists(atPath: $0) }),
      let srcImage = NSImage(contentsOfFile: sourcePath),
      let tiffData = srcImage.tiffRepresentation,
      let srcRep = NSBitmapImageRep(data: tiffData) else {
    fatalError("Could not load master logo_source.png")
}

let masterSize = 584

// Bilinear color sampling from srcRep (1024x1024)
func sampleBilinear(_ rep: NSBitmapImageRep, x: CGFloat, y: CGFloat) -> (r: CGFloat, g: CGFloat, b: CGFloat) {
    let w = CGFloat(rep.pixelsWide - 1)
    let h = CGFloat(rep.pixelsHigh - 1)
    let cx = min(max(x, 0), w)
    let cy = min(max(y, 0), h)

    let x0 = Int(floor(cx))
    let y0 = Int(floor(cy))
    let x1 = min(x0 + 1, Int(w))
    let y1 = min(y0 + 1, Int(h))
    let fx = cx - CGFloat(x0)
    let fy = cy - CGFloat(y0)

    let c00 = rep.colorAt(x: x0, y: y0)!.usingColorSpace(.deviceRGB)!
    let c10 = rep.colorAt(x: x1, y: y0)!.usingColorSpace(.deviceRGB)!
    let c01 = rep.colorAt(x: x0, y: y1)!.usingColorSpace(.deviceRGB)!
    let c11 = rep.colorAt(x: x1, y: y1)!.usingColorSpace(.deviceRGB)!

    let r = (c00.redComponent * (1 - fx) + c10.redComponent * fx) * (1 - fy) +
            (c01.redComponent * (1 - fx) + c11.redComponent * fx) * fy
    let g = (c00.greenComponent * (1 - fx) + c10.greenComponent * fx) * (1 - fy) +
            (c01.greenComponent * (1 - fx) + c11.greenComponent * fx) * fy
    let b = (c00.blueComponent * (1 - fx) + c10.blueComponent * fx) * (1 - fy) +
            (c01.blueComponent * (1 - fx) + c11.blueComponent * fx) * fy
    return (r, g, b)
}

func makeMasterBitmaps() -> (
    squircleFull: NSBitmapImageRep,
    squircleNoDrops: NSBitmapImageRep,
    brainNoDrops: NSBitmapImageRep
) {
    let squircleFull = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: masterSize,
        pixelsHigh: masterSize,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    )!

    let squircleNoDrops = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: masterSize,
        pixelsHigh: masterSize,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    )!

    let brainNoDrops = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: masterSize,
        pixelsHigh: masterSize,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    )!

    // Radial soft-feathered erasure weight (0.0 = keep 100%, 1.0 = erase droplet completely)
    // for the 4 detached droplets at the bottom of the logo
    let droplets: [(cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat)] = [
        (451.0, 693.0, 17.5, 25.0), // Drop 1: middle-left teardrop
        (585.0, 718.0, 11.5, 11.5), // Drop 2: center-right small droplet
        (622.0, 691.5, 12.5, 12.5), // Drop 3: right small droplet
        (651.0, 653.5, 11.5, 11.5)  // Drop 4: far-right small droplet
    ]

    func dropletEraseWeight(_ sx: CGFloat, _ sy: CGFloat) -> CGFloat {
        var maxWeight: CGFloat = 0.0
        for d in droplets {
            let dx = (sx - d.cx) / d.rx
            let dy = (sy - d.cy) / d.ry
            let dist = hypot(dx, dy)
            if dist < 1.0 {
                return 1.0
            } else if dist < 1.45 {
                let w = 1.0 - (dist - 1.0) / 0.45
                if w > maxWeight { maxWeight = w }
            }
        }
        return maxWeight
    }

    let halfMaster = CGFloat(masterSize) * 0.5
    // Map squircle [236..787] (552px) and [221..802] (582px) to symmetric 572x572 square centered in 584x584
    let scaleX: CGFloat = 552.0 / 572.0
    let scaleY: CGFloat = 582.0 / 572.0

    for cy in 0..<masterSize {
        let sy = 511.5 + (CGFloat(cy) + 0.5 - halfMaster) * scaleY

        // Sample clean dark-slate background color at this row inside the squircle (left & right of the brain)
        let clampedSy = min(max(sy, 240.0), 775.0)
        let bgLeft = sampleBilinear(srcRep, x: 275.0, y: clampedSy)
        let bgRight = sampleBilinear(srcRep, x: 748.0, y: clampedSy)

        for cx in 0..<masterSize {
            let sx = 511.5 + (CGFloat(cx) + 0.5 - halfMaster) * scaleX

            guard sx >= 235.0 && sx <= 788.0 && sy >= 220.0 && sy <= 803.0 else {
                let clear = NSColor(deviceRed: 0, green: 0, blue: 0, alpha: 0)
                squircleFull.setColor(clear, atX: cx, y: cy)
                squircleNoDrops.setColor(clear, atX: cx, y: cy)
                brainNoDrops.setColor(clear, atX: cx, y: cy)
                continue
            }

            let c = sampleBilinear(srcRep, x: sx, y: sy)
            let r = c.r
            let g = c.g
            let b = c.b

            // 1. Compute squircle mask alpha (separating dark squircle from light gray studio backdrop)
            let isInteriorCore = (sx >= 300.0 && sx <= 725.0) && (sy >= 295.0 && sy <= 725.0)
            let sqAlpha: CGFloat
            if isInteriorCore {
                sqAlpha = 1.0
            } else {
                let b255 = b * 255.0
                sqAlpha = min(1.0, max(0.0, (166.0 - b255) / (166.0 - 122.0)))
            }

            if sqAlpha <= 0.001 {
                let clear = NSColor(deviceRed: 0, green: 0, blue: 0, alpha: 0)
                squircleFull.setColor(clear, atX: cx, y: cy)
                squircleNoDrops.setColor(clear, atX: cx, y: cy)
                brainNoDrops.setColor(clear, atX: cx, y: cy)
                continue
            }

            // Un-blend exterior light gray (~0.82) on partially transparent perimeter pixels to avoid any light halo
            let cleanR: CGFloat
            let cleanG: CGFloat
            let cleanB: CGFloat
            if sqAlpha < 0.999 && !isInteriorCore {
                let ext: CGFloat = 0.82
                cleanR = min(0.35, max(0.0, (r - (1.0 - sqAlpha) * ext) / sqAlpha))
                cleanG = min(0.40, max(0.0, (g - (1.0 - sqAlpha) * ext) / sqAlpha))
                cleanB = min(0.45, max(0.0, (b - (1.0 - sqAlpha) * ext) / sqAlpha))
            } else {
                cleanR = r
                cleanG = g
                cleanB = b
            }

            let fullColor = NSColor(deviceRed: cleanR, green: cleanG, blue: cleanB, alpha: sqAlpha)
            squircleFull.setColor(fullColor, atX: cx, y: cy)

            // Interpolated background color at (sx, sy)
            let t = min(1.0, max(0.0, (sx - 275.0) / (748.0 - 275.0)))
            let bgR = bgLeft.r * (1.0 - t) + bgRight.r * t
            let bgG = bgLeft.g * (1.0 - t) + bgRight.g * t
            let bgB = bgLeft.b * (1.0 - t) + bgRight.b * t

            let eraseW = dropletEraseWeight(sx, sy)
            let noDropR = cleanR * (1.0 - eraseW) + bgR * eraseW
            let noDropG = cleanG * (1.0 - eraseW) + bgG * eraseW
            let noDropB = cleanB * (1.0 - eraseW) + bgB * eraseW
            squircleNoDrops.setColor(NSColor(deviceRed: noDropR, green: noDropG, blue: noDropB, alpha: sqAlpha), atX: cx, y: cy)

            // Extract neon red brain & glow layer via inverse alpha-over against (bgR, bgG, bgB)
            let inBrainBounds = (sx >= 295.0 && sx <= 735.0) && (sy >= 290.0 && sy <= 735.0)
            if inBrainBounds && noDropR > bgR + 0.015 && (noDropR - noDropG) > 0.02 {
                let bAlpha = min(1.0, max(0.0, (noDropR - bgR) / max(0.05, 1.0 - bgR))) * (1.0 - eraseW)
                if bAlpha > 0.002 {
                    let fgR: CGFloat = 1.0
                    let fgG = min(1.0, max(0.0, (noDropG - (1.0 - bAlpha) * bgG) / max(0.01, bAlpha)))
                    let fgB = min(1.0, max(0.0, (noDropB - (1.0 - bAlpha) * bgB) / max(0.01, bAlpha)))
                    brainNoDrops.setColor(NSColor(deviceRed: fgR, green: fgG, blue: fgB, alpha: bAlpha), atX: cx, y: cy)
                } else {
                    brainNoDrops.setColor(NSColor(deviceRed: 0, green: 0, blue: 0, alpha: 0), atX: cx, y: cy)
                }
            } else {
                brainNoDrops.setColor(NSColor(deviceRed: 0, green: 0, blue: 0, alpha: 0), atX: cx, y: cy)
            }
        }
    }

    return (squircleFull, squircleNoDrops, brainNoDrops)
}

func renderScaledPNG(from masterRep: NSBitmapImageRep, pixelSize: Int, insetFraction: CGFloat = 0.0) -> Data {
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
    let ctx = NSGraphicsContext(bitmapImageRep: rep)!
    ctx.imageInterpolation = .high
    NSGraphicsContext.current = ctx

    let s = CGFloat(pixelSize)
    let inset = s * insetFraction
    let destRect = NSRect(x: inset, y: inset, width: s - inset * 2, height: s - inset * 2)

    let img = NSImage(size: NSSize(width: masterRep.pixelsWide, height: masterRep.pixelsHigh))
    img.addRepresentation(masterRep)
    img.draw(in: destRect, from: .zero, operation: .sourceOver, fraction: 1.0)

    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])!
}

func createICOFile(from masterRep: NSBitmapImageRep, outputURL: URL) {
    let icoSizes = [16, 32, 48, 64, 128, 256]
    let pngFrames: [(Int, Data)] = icoSizes.map { sz in
        (sz, renderScaledPNG(from: masterRep, pixelSize: sz, insetFraction: 0.0))
    }

    var icoData = Data()
    func appendUInt16(_ val: UInt16) {
        var le = val.littleEndian
        icoData.append(Data(bytes: &le, count: 2))
    }
    func appendUInt32(_ val: UInt32) {
        var le = val.littleEndian
        icoData.append(Data(bytes: &le, count: 4))
    }

    // ICONDIR Header (6 bytes)
    appendUInt16(0) // Reserved
    appendUInt16(1) // Type 1 = ICO
    appendUInt16(UInt16(pngFrames.count))

    // ICONDIRENTRY (16 bytes per frame)
    var currentOffset = UInt32(6 + pngFrames.count * 16)
    for (sz, data) in pngFrames {
        let dimByte: UInt8 = sz >= 256 ? 0 : UInt8(sz)
        icoData.append(dimByte) // width
        icoData.append(dimByte) // height
        icoData.append(0)       // palette count
        icoData.append(0)       // reserved
        appendUInt16(1)         // color planes
        appendUInt16(32)        // bits per pixel
        appendUInt32(UInt32(data.count))
        appendUInt32(currentOffset)
        currentOffset += UInt32(data.count)
    }

    // Raw PNG payloads
    for (_, data) in pngFrames {
        icoData.append(data)
    }

    try? icoData.write(to: outputURL, options: .atomic)
}

let masters = makeMasterBitmaps()

// 1. Generate macOS AppIcon.iconset PNGs
let macIconSizes = [16, 32, 128, 256, 512]
for sz in macIconSizes {
    let png1x = renderScaledPNG(from: masters.squircleFull, pixelSize: sz, insetFraction: 0.035)
    try? png1x.write(to: URL(fileURLWithPath: outputDir).appendingPathComponent("icon_\(sz)x\(sz).png"))

    let png2x = renderScaledPNG(from: masters.squircleFull, pixelSize: sz * 2, insetFraction: 0.035)
    try? png2x.write(to: URL(fileURLWithPath: outputDir).appendingPathComponent("icon_\(sz)x\(sz)@2x.png"))
}

// 2. Save high-res 1024x1024 master assets in shared/logo/
let rootCandidate = fm.fileExists(atPath: "../shared") ? ".." : "."
let sharedLogoDir = "\(rootCandidate)/shared/logo"
try? fm.createDirectory(atPath: sharedLogoDir, withIntermediateDirectories: true)

let full1024 = renderScaledPNG(from: masters.squircleFull, pixelSize: 1024, insetFraction: 0.0)
let noDropsSquircle1024 = renderScaledPNG(from: masters.squircleNoDrops, pixelSize: 1024, insetFraction: 0.0)
let noDropsBrain1024 = renderScaledPNG(from: masters.brainNoDrops, pixelSize: 1024, insetFraction: 0.0)

try? full1024.write(to: URL(fileURLWithPath: "\(sharedLogoDir)/app_logo_squircle.png"))
try? noDropsSquircle1024.write(to: URL(fileURLWithPath: "\(sharedLogoDir)/app_logo_squircle_nodrops.png"))
try? noDropsBrain1024.write(to: URL(fileURLWithPath: "\(sharedLogoDir)/app_logo_brain_nodrops.png"))

// 3. Generate multi-resolution .ico files
createICOFile(from: masters.squircleFull, outputURL: URL(fileURLWithPath: "\(rootCandidate)/AppIcon.ico"))
createICOFile(from: masters.squircleFull, outputURL: URL(fileURLWithPath: "\(sharedLogoDir)/AppIcon.ico"))
createICOFile(from: masters.squircleFull, outputURL: URL(fileURLWithPath: "\(rootCandidate)/macos/AppIcon.ico"))

// 4. Update Android drawable & mipmap resources if present
let androidResPath = "\(rootCandidate)/android/app/src/main/res"
if fm.fileExists(atPath: androidResPath) {
    let drawable512 = renderScaledPNG(from: masters.squircleFull, pixelSize: 512, insetFraction: 0.0)
    let drawableNoDrops512 = renderScaledPNG(from: masters.squircleNoDrops, pixelSize: 512, insetFraction: 0.0)
    let drawableBrain512 = renderScaledPNG(from: masters.brainNoDrops, pixelSize: 512, insetFraction: 0.0)

    try? drawable512.write(to: URL(fileURLWithPath: "\(androidResPath)/drawable/bloody_brain_icon.png"))
    try? drawableNoDrops512.write(to: URL(fileURLWithPath: "\(androidResPath)/drawable/bloody_brain_squircle_nodrops.png"))
    try? drawableBrain512.write(to: URL(fileURLWithPath: "\(androidResPath)/drawable/bloody_brain_neon_nodrops.png"))

    let mipmaps: [(String, Int)] = [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192)
    ]
    for (folder, px) in mipmaps {
        let data = renderScaledPNG(from: masters.squircleFull, pixelSize: px, insetFraction: 0.0)
        try? data.write(to: URL(fileURLWithPath: "\(androidResPath)/\(folder)/ic_launcher.png"))
    }
}
