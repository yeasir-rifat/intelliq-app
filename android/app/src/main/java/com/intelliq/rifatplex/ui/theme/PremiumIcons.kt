package com.intelliq.rifatplex.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Premium icon set: Phosphor Icons (MIT, https://phosphoricons.com) - Duotone for the main icons,
 * Bold for tiny glyphs (arrows, close, plus). Generated from the official SVGs (256x256 viewBox).
 * The faint duotone layer keeps 20% alpha, so `Icon(tint = ...)` colours both layers correctly.
 * Names match the old Material icon names so call sites read `PIcons.Download` etc.
 */
object PIcons {

    val Close: ImageVector by lazy {
        ImageVector.Builder("Close", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M208.49,191.51a12,12,0,0,1-17,17L128,145,64.49,208.49a12,12,0,0,1-17-17L111,128,47.51,64.49a12,12,0,0,1,17-17L128,111l63.51-63.52a12,12,0,0,1,17,17L145,128Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Visibility: ImageVector by lazy {
        ImageVector.Builder("Visibility", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M128,56C48,56,16,128,16,128s32,72,112,72,112-72,112-72S208,56,128,56Zm0,112a40,40,0,1,1,40-40A40,40,0,0,1,128,168Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M247.31,124.76c-.35-.79-8.82-19.58-27.65-38.41C194.57,61.26,162.88,48,128,48S61.43,61.26,36.34,86.35C17.51,105.18,9,124,8.69,124.76a8,8,0,0,0,0,6.5c.35.79,8.82,19.57,27.65,38.4C61.43,194.74,93.12,208,128,208s66.57-13.26,91.66-38.34c18.83-18.83,27.3-37.61,27.65-38.4A8,8,0,0,0,247.31,124.76ZM128,192c-30.78,0-57.67-11.19-79.93-33.25A133.47,133.47,0,0,1,25,128,133.33,133.33,0,0,1,48.07,97.25C70.33,75.19,97.22,64,128,64s57.67,11.19,79.93,33.25A133.46,133.46,0,0,1,231.05,128C223.84,141.46,192.43,192,128,192Zm0-112a48,48,0,1,0,48,48A48.05,48.05,0,0,0,128,80Zm0,80a32,32,0,1,1,32-32A32,32,0,0,1,128,160Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder("Download", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216,48V208H40V48A16,16,0,0,1,56,32H200A16,16,0,0,1,216,48Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M224,144v64a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V144a8,8,0,0,1,16,0v56H208V144a8,8,0,0,1,16,0Zm-101.66,5.66a8,8,0,0,0,11.32,0l40-40a8,8,0,0,0-11.32-11.32L136,124.69V32a8,8,0,0,0-16,0v92.69L93.66,98.34a8,8,0,0,0-11.32,11.32Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val KeyboardArrowDown: ImageVector by lazy {
        ImageVector.Builder("KeyboardArrowDown", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216.49,104.49l-80,80a12,12,0,0,1-17,0l-80-80a12,12,0,0,1,17-17L128,159l71.51-71.52a12,12,0,0,1,17,17Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val KeyboardArrowUp: ImageVector by lazy {
        ImageVector.Builder("KeyboardArrowUp", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216.49,168.49a12,12,0,0,1-17,0L128,97,56.49,168.49a12,12,0,0,1-17-17l80-80a12,12,0,0,1,17,0l80,80A12,12,0,0,1,216.49,168.49Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val ExpandMore: ImageVector by lazy {
        ImageVector.Builder("ExpandMore", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216.49,104.49l-80,80a12,12,0,0,1-17,0l-80-80a12,12,0,0,1,17-17L128,159l71.51-71.52a12,12,0,0,1,17,17Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val ExpandLess: ImageVector by lazy {
        ImageVector.Builder("ExpandLess", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216.49,168.49a12,12,0,0,1-17,0L128,97,56.49,168.49a12,12,0,0,1-17-17l80-80a12,12,0,0,1,17,0l80,80A12,12,0,0,1,216.49,168.49Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Folder: ImageVector by lazy {
        ImageVector.Builder("Folder", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M128,80H32V56a8,8,0,0,1,8-8H92.69a8,8,0,0,1,5.65,2.34Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M216,72H131.31L104,44.69A15.86,15.86,0,0,0,92.69,40H40A16,16,0,0,0,24,56V200.62A15.4,15.4,0,0,0,39.38,216H216.89A15.13,15.13,0,0,0,232,200.89V88A16,16,0,0,0,216,72ZM92.69,56l16,16H40V56ZM216,200H40V88H216Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Edit: ImageVector by lazy {
        ImageVector.Builder("Edit", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M221.66,90.34,192,120,136,64l29.66-29.66a8,8,0,0,1,11.31,0L221.66,79A8,8,0,0,1,221.66,90.34Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M227.32,73.37,182.63,28.69a16,16,0,0,0-22.63,0L36.69,152A15.86,15.86,0,0,0,32,163.31V208a16,16,0,0,0,16,16H216a8,8,0,0,0,0-16H115.32l112-112A16,16,0,0,0,227.32,73.37ZM48,163.31l88-88L180.69,120l-88,88H48Zm144-54.62L147.32,64l24-24L216,84.69Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Description: ImageVector by lazy {
        ImageVector.Builder("Description", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M208,88H152V32Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M213.66,82.34l-56-56A8,8,0,0,0,152,24H56A16,16,0,0,0,40,40V216a16,16,0,0,0,16,16H200a16,16,0,0,0,16-16V88A8,8,0,0,0,213.66,82.34ZM160,51.31,188.69,80H160ZM200,216H56V40h88V88a8,8,0,0,0,8,8h48V216Zm-32-80a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h64A8,8,0,0,1,168,136Zm0,32a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h64A8,8,0,0,1,168,168Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val AutoAwesome: ImageVector by lazy {
        ImageVector.Builder("AutoAwesome", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M194.82,151.43l-55.09,20.3-20.3,55.09a7.92,7.92,0,0,1-14.86,0l-20.3-55.09-55.09-20.3a7.92,7.92,0,0,1,0-14.86l55.09-20.3,20.3-55.09a7.92,7.92,0,0,1,14.86,0l20.3,55.09,55.09,20.3A7.92,7.92,0,0,1,194.82,151.43Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M197.58,129.06,146,110l-19-51.62a15.92,15.92,0,0,0-29.88,0L78,110l-51.62,19a15.92,15.92,0,0,0,0,29.88L78,178l19,51.62a15.92,15.92,0,0,0,29.88,0L146,178l51.62-19a15.92,15.92,0,0,0,0-29.88ZM137,164.22a8,8,0,0,0-4.74,4.74L112,223.85,91.78,169A8,8,0,0,0,87,164.22L32.15,144,87,123.78A8,8,0,0,0,91.78,119L112,64.15,132.22,119a8,8,0,0,0,4.74,4.74L191.85,144ZM144,40a8,8,0,0,1,8-8h16V16a8,8,0,0,1,16,0V32h16a8,8,0,0,1,0,16H184V64a8,8,0,0,1-16,0V48H152A8,8,0,0,1,144,40ZM248,88a8,8,0,0,1-8,8h-8v8a8,8,0,0,1-16,0V96h-8a8,8,0,0,1,0-16h8V72a8,8,0,0,1,16,0v8h8A8,8,0,0,1,248,88Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Inbox: ImageVector by lazy {
        ImageVector.Builder("Inbox", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216,48V160H179.31a8,8,0,0,0-5.66,2.34l-19.31,19.32a8,8,0,0,1-5.66,2.34H107.31a8,8,0,0,1-5.66-2.34L82.34,162.34A8,8,0,0,0,76.68,160H40V48a8,8,0,0,1,8-8H208A8,8,0,0,1,216,48Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M208,32H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48A16,16,0,0,0,208,32Zm0,16V152h-28.7A15.86,15.86,0,0,0,168,156.69L148.69,176H107.31L88,156.68A15.89,15.89,0,0,0,76.69,152H48V48Zm0,160H48V168H76.69L96,187.32A15.89,15.89,0,0,0,107.31,192h41.38A15.86,15.86,0,0,0,160,187.31L179.31,168H208v40Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val History: ImageVector by lazy {
        ImageVector.Builder("History", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M140,80v41.21l34.17,20.5a12,12,0,1,1-12.34,20.58l-40-24A12,12,0,0,1,116,128V80a12,12,0,0,1,24,0ZM128,28A99.38,99.38,0,0,0,57.24,57.34c-4.69,4.74-9,9.37-13.24,14V64a12,12,0,0,0-24,0v40a12,12,0,0,0,12,12H72a12,12,0,0,0,0-24H57.77C63,86,68.37,80.22,74.26,74.26a76,76,0,1,1,1.58,109,12,12,0,0,0-16.48,17.46A100,100,0,1,0,128,28Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val DeleteOutline: ImageVector by lazy {
        ImageVector.Builder("DeleteOutline", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M200,56V208a8,8,0,0,1-8,8H64a8,8,0,0,1-8-8V56Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M216,48H176V40a24,24,0,0,0-24-24H104A24,24,0,0,0,80,40v8H40a8,8,0,0,0,0,16h8V208a16,16,0,0,0,16,16H192a16,16,0,0,0,16-16V64h8a8,8,0,0,0,0-16ZM96,40a8,8,0,0,1,8-8h48a8,8,0,0,1,8,8v8H96Zm96,168H64V64H192ZM112,104v64a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Zm48,0v64a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Add: ImageVector by lazy {
        ImageVector.Builder("Add", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M228,128a12,12,0,0,1-12,12H140v76a12,12,0,0,1-24,0V140H40a12,12,0,0,1,0-24h76V40a12,12,0,0,1,24,0v76h76A12,12,0,0,1,228,128Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Remove: ImageVector by lazy {
        ImageVector.Builder("Remove", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M228,128a12,12,0,0,1-12,12H40a12,12,0,0,1,0-24H216A12,12,0,0,1,228,128Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val ViewColumn: ImageVector by lazy {
        ImageVector.Builder("ViewColumn", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M112,48V208a8,8,0,0,1-8,8H64a8,8,0,0,1-8-8V48a8,8,0,0,1,8-8h40A8,8,0,0,1,112,48Zm80-8H152a8,8,0,0,0-8,8V208a8,8,0,0,0,8,8h40a8,8,0,0,0,8-8V48A8,8,0,0,0,192,40Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M104,32H64A16,16,0,0,0,48,48V208a16,16,0,0,0,16,16h40a16,16,0,0,0,16-16V48A16,16,0,0,0,104,32Zm0,176H64V48h40ZM192,32H152a16,16,0,0,0-16,16V208a16,16,0,0,0,16,16h40a16,16,0,0,0,16-16V48A16,16,0,0,0,192,32Zm0,176H152V48h40Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val ViewAgenda: ImageVector by lazy {
        ImageVector.Builder("ViewAgenda", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216,152v40a8,8,0,0,1-8,8H48a8,8,0,0,1-8-8V152a8,8,0,0,1,8-8H208A8,8,0,0,1,216,152Zm-8-96H48a8,8,0,0,0-8,8v40a8,8,0,0,0,8,8H208a8,8,0,0,0,8-8V64A8,8,0,0,0,208,56Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M208,136H48a16,16,0,0,0-16,16v40a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V152A16,16,0,0,0,208,136Zm0,56H48V152H208v40Zm0-144H48A16,16,0,0,0,32,64v40a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V64A16,16,0,0,0,208,48Zm0,56H48V64H208v40Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Upload: ImageVector by lazy {
        ImageVector.Builder("Upload", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216,48V208H40V48A16,16,0,0,1,56,32H200A16,16,0,0,1,216,48Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M224,144v64a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V144a8,8,0,0,1,16,0v56H208V144a8,8,0,0,1,16,0ZM93.66,77.66,120,51.31V144a8,8,0,0,0,16,0V51.31l26.34,26.35a8,8,0,0,0,11.32-11.32l-40-40a8,8,0,0,0-11.32,0l-40,40A8,8,0,0,0,93.66,77.66Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val TextFormat: ImageVector by lazy {
        ImageVector.Builder("TextFormat", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M232,164c0,15.46-14.33,28-32,28s-32-12.54-32-28,14.33-28,32-28S232,148.54,232,164ZM34.82,152h90.36L80,56Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M87.24,52.59a8,8,0,0,0-14.48,0l-64,136a8,8,0,1,0,14.48,6.81L39.9,160h80.2l16.66,35.4a8,8,0,1,0,14.48-6.81ZM47.43,144,80,74.79,112.57,144ZM200,96c-12.76,0-22.73,3.47-29.63,10.32a8,8,0,0,0,11.26,11.36c3.8-3.77,10-5.68,18.37-5.68,13.23,0,24,9,24,20v3.22A42.76,42.76,0,0,0,200,128c-22.06,0-40,16.15-40,36s17.94,36,40,36a42.73,42.73,0,0,0,24-7.25,8,8,0,0,0,16-.75V132C240,112.15,222.06,96,200,96Zm0,88c-13.23,0-24-9-24-20s10.77-20,24-20,24,9,24,20S213.23,184,200,184Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Slideshow: ImageVector by lazy {
        ImageVector.Builder("Slideshow", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M224,56V176a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V56a8,8,0,0,1,8-8H216A8,8,0,0,1,224,56Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M216,40H136V24a8,8,0,0,0-16,0V40H40A16,16,0,0,0,24,56V176a16,16,0,0,0,16,16H79.36L57.75,219a8,8,0,0,0,12.5,10l29.59-37h56.32l29.59,37a8,8,0,1,0,12.5-10l-21.61-27H216a16,16,0,0,0,16-16V56A16,16,0,0,0,216,40Zm0,136H40V56H216V176ZM104,120v24a8,8,0,0,1-16,0V120a8,8,0,0,1,16,0Zm32-16v40a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Zm32-16v56a8,8,0,0,1-16,0V88a8,8,0,0,1,16,0Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder("Settings", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M230.1,108.76,198.25,90.62c-.64-1.16-1.31-2.29-2-3.41l-.12-36A104.61,104.61,0,0,0,162,32L130,49.89c-1.34,0-2.69,0-4,0L94,32A104.58,104.58,0,0,0,59.89,51.25l-.16,36c-.7,1.12-1.37,2.26-2,3.41l-31.84,18.1a99.15,99.15,0,0,0,0,38.46l31.85,18.14c.64,1.16,1.31,2.29,2,3.41l.12,36A104.61,104.61,0,0,0,94,224l32-17.87c1.34,0,2.69,0,4,0L162,224a104.58,104.58,0,0,0,34.08-19.25l.16-36c.7-1.12,1.37-2.26,2-3.41l31.84-18.1A99.15,99.15,0,0,0,230.1,108.76ZM128,168a40,40,0,1,1,40-40A40,40,0,0,1,128,168Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M128,80a48,48,0,1,0,48,48A48.05,48.05,0,0,0,128,80Zm0,80a32,32,0,1,1,32-32A32,32,0,0,1,128,160Zm109.94-52.79a8,8,0,0,0-3.89-5.4l-29.83-17-.12-33.62a8,8,0,0,0-2.83-6.08,111.91,111.91,0,0,0-36.72-20.67,8,8,0,0,0-6.46.59L128,41.85,97.88,25a8,8,0,0,0-6.47-.6A111.92,111.92,0,0,0,54.73,45.15a8,8,0,0,0-2.83,6.07l-.15,33.65-29.83,17a8,8,0,0,0-3.89,5.4,106.47,106.47,0,0,0,0,41.56,8,8,0,0,0,3.89,5.4l29.83,17,.12,33.63a8,8,0,0,0,2.83,6.08,111.91,111.91,0,0,0,36.72,20.67,8,8,0,0,0,6.46-.59L128,214.15,158.12,231a7.91,7.91,0,0,0,3.9,1,8.09,8.09,0,0,0,2.57-.42,112.1,112.1,0,0,0,36.68-20.73,8,8,0,0,0,2.83-6.07l.15-33.65,29.83-17a8,8,0,0,0,3.89-5.4A106.47,106.47,0,0,0,237.94,107.21Zm-15,34.91-28.57,16.25a8,8,0,0,0-3,3c-.58,1-1.19,2.06-1.81,3.06a7.94,7.94,0,0,0-1.22,4.21l-.15,32.25a95.89,95.89,0,0,1-25.37,14.3L134,199.13a8,8,0,0,0-3.91-1h-.19c-1.21,0-2.43,0-3.64,0a8.1,8.1,0,0,0-4.1,1l-28.84,16.1A96,96,0,0,1,67.88,201l-.11-32.2a8,8,0,0,0-1.22-4.22c-.62-1-1.23-2-1.8-3.06a8.09,8.09,0,0,0-3-3.06l-28.6-16.29a90.49,90.49,0,0,1,0-28.26L61.67,97.63a8,8,0,0,0,3-3c.58-1,1.19-2.06,1.81-3.06a7.94,7.94,0,0,0,1.22-4.21l.15-32.25a95.89,95.89,0,0,1,25.37-14.3L122,56.87a8,8,0,0,0,4.1,1c1.21,0,2.43,0,3.64,0a8,8,0,0,0,4.1-1l28.84-16.1A96,96,0,0,1,188.12,55l.11,32.2a8,8,0,0,0,1.22,4.22c.62,1,1.23,2,1.8,3.06a8.09,8.09,0,0,0,3,3.06l28.6,16.29A90.49,90.49,0,0,1,222.9,142.12Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Save: ImageVector by lazy {
        ImageVector.Builder("Save", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216,83.31V208a8,8,0,0,1-8,8H176V152a8,8,0,0,0-8-8H88a8,8,0,0,0-8,8v64H48a8,8,0,0,1-8-8V48a8,8,0,0,1,8-8H172.69a8,8,0,0,1,5.65,2.34l35.32,35.32A8,8,0,0,1,216,83.31Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M219.31,72,184,36.69A15.86,15.86,0,0,0,172.69,32H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V83.31A15.86,15.86,0,0,0,219.31,72ZM168,208H88V152h80Zm40,0H184V152a16,16,0,0,0-16-16H88a16,16,0,0,0-16,16v56H48V48H172.69L208,83.31ZM160,72a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h56A8,8,0,0,1,160,72Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val RestartAlt: ImageVector by lazy {
        ImageVector.Builder("RestartAlt", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M228,128a100,100,0,0,1-98.66,100H128a99.39,99.39,0,0,1-68.62-27.29,12,12,0,0,1,16.48-17.45,76,76,0,1,0-1.57-109c-.13.13-.25.25-.39.37L54.89,92H72a12,12,0,0,1,0,24H24a12,12,0,0,1-12-12V56a12,12,0,0,1,24,0V76.72L57.48,57.06A100,100,0,0,1,228,128Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val PictureAsPdf: ImageVector by lazy {
        ImageVector.Builder("PictureAsPdf", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M208,88H152V32Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M224,152a8,8,0,0,1-8,8H192v16h16a8,8,0,0,1,0,16H192v16a8,8,0,0,1-16,0V152a8,8,0,0,1,8-8h32A8,8,0,0,1,224,152ZM92,172a28,28,0,0,1-28,28H56v8a8,8,0,0,1-16,0V152a8,8,0,0,1,8-8H64A28,28,0,0,1,92,172Zm-16,0a12,12,0,0,0-12-12H56v24h8A12,12,0,0,0,76,172Zm88,8a36,36,0,0,1-36,36H112a8,8,0,0,1-8-8V152a8,8,0,0,1,8-8h16A36,36,0,0,1,164,180Zm-16,0a20,20,0,0,0-20-20h-8v40h8A20,20,0,0,0,148,180ZM40,112V40A16,16,0,0,1,56,24h96a8,8,0,0,1,5.66,2.34l56,56A8,8,0,0,1,216,88v24a8,8,0,0,1-16,0V96H152a8,8,0,0,1-8-8V40H56v72a8,8,0,0,1-16,0ZM160,80h28.69L160,51.31Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder("Palette", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M224,127.17a96.48,96.48,0,0,1-2.39,22.18A24,24,0,0,1,198.21,168H152a24,24,0,0,0-24,24,24,24,0,0,1-32,22.61C58.73,201.44,32,169.81,32,128a96,96,0,0,1,95-96C179.84,31.47,223.55,74.35,224,127.17Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M200.77,53.89A103.27,103.27,0,0,0,128,24h-1.07A104,104,0,0,0,24,128c0,43,26.58,79.06,69.36,94.17A32,32,0,0,0,136,192a16,16,0,0,1,16-16h46.21a31.81,31.81,0,0,0,31.2-24.88,104.43,104.43,0,0,0,2.59-24A103.28,103.28,0,0,0,200.77,53.89Zm13,93.71A15.89,15.89,0,0,1,198.21,160H152a32,32,0,0,0-32,32,16,16,0,0,1-21.31,15.07C62.49,194.3,40,164,40,128a88,88,0,0,1,87.09-88h.9a88.35,88.35,0,0,1,88,87.25A88.86,88.86,0,0,1,213.81,147.6ZM140,76a12,12,0,1,1-12-12A12,12,0,0,1,140,76ZM96,100A12,12,0,1,1,84,88,12,12,0,0,1,96,100Zm0,56a12,12,0,1,1-12-12A12,12,0,0,1,96,156Zm88-56a12,12,0,1,1-12-12A12,12,0,0,1,184,100Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val LightMode: ImageVector by lazy {
        ImageVector.Builder("LightMode", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M184,128a56,56,0,1,1-56-56A56,56,0,0,1,184,128Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M120,40V16a8,8,0,0,1,16,0V40a8,8,0,0,1-16,0Zm72,88a64,64,0,1,1-64-64A64.07,64.07,0,0,1,192,128Zm-16,0a48,48,0,1,0-48,48A48.05,48.05,0,0,0,176,128ZM58.34,69.66A8,8,0,0,0,69.66,58.34l-16-16A8,8,0,0,0,42.34,53.66Zm0,116.68-16,16a8,8,0,0,0,11.32,11.32l16-16a8,8,0,0,0-11.32-11.32ZM192,72a8,8,0,0,0,5.66-2.34l16-16a8,8,0,0,0-11.32-11.32l-16,16A8,8,0,0,0,192,72Zm5.66,114.34a8,8,0,0,0-11.32,11.32l16,16a8,8,0,0,0,11.32-11.32ZM48,128a8,8,0,0,0-8-8H16a8,8,0,0,0,0,16H40A8,8,0,0,0,48,128Zm80,80a8,8,0,0,0-8,8v24a8,8,0,0,0,16,0V216A8,8,0,0,0,128,208Zm112-88H216a8,8,0,0,0,0,16h24a8,8,0,0,0,0-16Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val DarkMode: ImageVector by lazy {
        ImageVector.Builder("DarkMode", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M227.89,147.89A96,96,0,1,1,108.11,28.11,96.09,96.09,0,0,0,227.89,147.89Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M233.54,142.23a8,8,0,0,0-8-2,88.08,88.08,0,0,1-109.8-109.8,8,8,0,0,0-10-10,104.84,104.84,0,0,0-52.91,37A104,104,0,0,0,136,224a103.09,103.09,0,0,0,62.52-20.88,104.84,104.84,0,0,0,37-52.91A8,8,0,0,0,233.54,142.23ZM188.9,190.34A88,88,0,0,1,65.66,67.11a89,89,0,0,1,31.4-26A106,106,0,0,0,96,56,104.11,104.11,0,0,0,200,160a106,106,0,0,0,14.92-1.06A89,89,0,0,1,188.9,190.34Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Image: ImageVector by lazy {
        ImageVector.Builder("Image", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M224,56V178.06l-39.72-39.72a8,8,0,0,0-11.31,0L147.31,164,97.66,114.34a8,8,0,0,0-11.32,0L32,168.69V56a8,8,0,0,1,8-8H216A8,8,0,0,1,224,56Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M216,40H40A16,16,0,0,0,24,56V200a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V56A16,16,0,0,0,216,40Zm0,16V158.75l-26.07-26.06a16,16,0,0,0-22.63,0l-20,20-44-44a16,16,0,0,0-22.62,0L40,149.37V56ZM40,172l52-52,80,80H40Zm176,28H194.63l-36-36,20-20L216,181.38V200ZM144,100a12,12,0,1,1,12,12A12,12,0,0,1,144,100Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val GridView: ImageVector by lazy {
        ImageVector.Builder("GridView", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M112,56v48a8,8,0,0,1-8,8H56a8,8,0,0,1-8-8V56a8,8,0,0,1,8-8h48A8,8,0,0,1,112,56Zm88-8H152a8,8,0,0,0-8,8v48a8,8,0,0,0,8,8h48a8,8,0,0,0,8-8V56A8,8,0,0,0,200,48Zm-96,96H56a8,8,0,0,0-8,8v48a8,8,0,0,0,8,8h48a8,8,0,0,0,8-8V152A8,8,0,0,0,104,144Zm96,0H152a8,8,0,0,0-8,8v48a8,8,0,0,0,8,8h48a8,8,0,0,0,8-8V152A8,8,0,0,0,200,144Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M200,136H152a16,16,0,0,0-16,16v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V152A16,16,0,0,0,200,136Zm0,64H152V152h48v48ZM104,40H56A16,16,0,0,0,40,56v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V56A16,16,0,0,0,104,40Zm0,64H56V56h48v48Zm96-64H152a16,16,0,0,0-16,16v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V56A16,16,0,0,0,200,40Zm0,64H152V56h48v48Zm-96,32H56a16,16,0,0,0-16,16v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V152A16,16,0,0,0,104,136Zm0,64H56V152h48v48Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val FormatClear: ImageVector by lazy {
        ImageVector.Builder("FormatClear", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216.07,224.88a12,12,0,0,1-16.95-.81L140,159v29h20a12,12,0,0,1,0,24H96a12,12,0,0,1,0-24h20V132.64L68,79.84V88a12,12,0,0,1-24,0V56a11.75,11.75,0,0,1,.23-2.31l-5.11-5.62A12,12,0,1,1,56.88,31.93l160,176A12,12,0,0,1,216.07,224.88ZM116,68v.57a12,12,0,1,0,24,0V68h48V88a12,12,0,0,0,24,0V56a12,12,0,0,0-12-12H116.6a12,12,0,0,0-.6,24Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val CreateNewFolder: ImageVector by lazy {
        ImageVector.Builder("CreateNewFolder", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M128,80H32V56a8,8,0,0,1,8-8H92.69a8,8,0,0,1,5.65,2.34Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M216,72H131.31L104,44.69A15.86,15.86,0,0,0,92.69,40H40A16,16,0,0,0,24,56V200.62A15.4,15.4,0,0,0,39.38,216H216.89A15.13,15.13,0,0,0,232,200.89V88A16,16,0,0,0,216,72ZM92.69,56l16,16H40V56ZM216,200H40V88H216Zm-88-88a8,8,0,0,1,8,8v16h16a8,8,0,0,1,0,16H136v16a8,8,0,0,1-16,0V152H104a8,8,0,0,1,0-16h16V120A8,8,0,0,1,128,112Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val ContentCopy: ImageVector by lazy {
        ImageVector.Builder("ContentCopy", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M216,40V168H168V88H88V40Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M216,32H88a8,8,0,0,0-8,8V80H40a8,8,0,0,0-8,8V216a8,8,0,0,0,8,8H168a8,8,0,0,0,8-8V176h40a8,8,0,0,0,8-8V40A8,8,0,0,0,216,32ZM160,208H48V96H160Zm48-48H176V88a8,8,0,0,0-8-8H96V48H208Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder("CheckCircle", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M224,128a96,96,0,1,1-96-96A96,96,0,0,1,224,128Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M173.66,98.34a8,8,0,0,1,0,11.32l-56,56a8,8,0,0,1-11.32,0l-24-24a8,8,0,0,1,11.32-11.32L112,148.69l50.34-50.35A8,8,0,0,1,173.66,98.34ZM232,128A104,104,0,1,1,128,24,104.11,104.11,0,0,1,232,128Zm-16,0a88,88,0,1,0-88,88A88.1,88.1,0,0,0,216,128Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder("Check", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M232.49,80.49l-128,128a12,12,0,0,1-17,0l-56-56a12,12,0,1,1,17-17L96,183,215.51,63.51a12,12,0,0,1,17,17Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val CameraAlt: ImageVector by lazy {
        ImageVector.Builder("CameraAlt", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M208,64H176L160,40H96L80,64H48A16,16,0,0,0,32,80V192a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V80A16,16,0,0,0,208,64ZM128,168a36,36,0,1,1,36-36A36,36,0,0,1,128,168Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M208,56H180.28L166.65,35.56A8,8,0,0,0,160,32H96a8,8,0,0,0-6.65,3.56L75.71,56H48A24,24,0,0,0,24,80V192a24,24,0,0,0,24,24H208a24,24,0,0,0,24-24V80A24,24,0,0,0,208,56Zm8,136a8,8,0,0,1-8,8H48a8,8,0,0,1-8-8V80a8,8,0,0,1,8-8H80a8,8,0,0,0,6.66-3.56L100.28,48h55.43l13.63,20.44A8,8,0,0,0,176,72h32a8,8,0,0,1,8,8ZM128,88a44,44,0,1,0,44,44A44.05,44.05,0,0,0,128,88Zm0,72a28,28,0,1,1,28-28A28,28,0,0,1,128,160Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val AutoFixHigh: ImageVector by lazy {
        ImageVector.Builder("AutoFixHigh", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M176,112,74.34,213.66a8,8,0,0,1-11.31,0L42.34,193a8,8,0,0,1,0-11.31L144,80Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M248,152a8,8,0,0,1-8,8H224v16a8,8,0,0,1-16,0V160H192a8,8,0,0,1,0-16h16V128a8,8,0,0,1,16,0v16h16A8,8,0,0,1,248,152ZM56,72H72V88a8,8,0,0,0,16,0V72h16a8,8,0,0,0,0-16H88V40a8,8,0,0,0-16,0V56H56a8,8,0,0,0,0,16ZM184,192h-8v-8a8,8,0,0,0-16,0v8h-8a8,8,0,0,0,0,16h8v8a8,8,0,0,0,16,0v-8h8a8,8,0,0,0,0-16ZM219.31,80,80,219.31a16,16,0,0,1-22.62,0L36.68,198.63a16,16,0,0,1,0-22.63L176,36.69a16,16,0,0,1,22.63,0l20.68,20.68A16,16,0,0,1,219.31,80Zm-54.63,32L144,91.31l-96,96L68.68,208ZM208,68.69,187.31,48l-32,32L176,100.69Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val AddPhotoAlternate: ImageVector by lazy {
        ImageVector.Builder("AddPhotoAlternate", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M208,40H48a8,8,0,0,0-8,8V208a8,8,0,0,0,8,8h8.69L166.34,106.34a8,8,0,0,1,11.32,0L216,144.69V48A8,8,0,0,0,208,40ZM96,112a16,16,0,1,1,16-16A16,16,0,0,1,96,112Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M208,32H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48A16,16,0,0,0,208,32ZM48,48H208v77.38l-24.69-24.7a16,16,0,0,0-22.62,0L53.37,208H48ZM208,208H76l96-96,36,36v60ZM96,120A24,24,0,1,0,72,96,24,24,0,0,0,96,120Zm0-32a8,8,0,1,1-8,8A8,8,0,0,1,96,88Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val AccountCircle: ImageVector by lazy {
        ImageVector.Builder("AccountCircle", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M224,128a95.76,95.76,0,0,1-31.8,71.37A72,72,0,0,0,128,160a40,40,0,1,0-40-40,40,40,0,0,0,40,40,72,72,0,0,0-64.2,39.37h0A96,96,0,1,1,224,128Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24ZM74.08,197.5a64,64,0,0,1,107.84,0,87.83,87.83,0,0,1-107.84,0ZM96,120a32,32,0,1,1,32,32A32,32,0,0,1,96,120Zm97.76,66.41a79.66,79.66,0,0,0-36.06-28.75,48,48,0,1,0-59.4,0,79.66,79.66,0,0,0-36.06,28.75,88,88,0,1,1,131.52,0Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val Menu: ImageVector by lazy {
        ImageVector.Builder("Menu", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M228,128a12,12,0,0,1-12,12H40a12,12,0,0,1,0-24H216A12,12,0,0,1,228,128ZM40,76H216a12,12,0,0,0,0-24H40a12,12,0,0,0,0,24ZM216,180H40a12,12,0,0,0,0,24H216a12,12,0,0,0,0-24Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    val ErrorCircle: ImageVector by lazy {
        ImageVector.Builder("ErrorCircle", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M224,128a96,96,0,1,1-96-96A96,96,0,0,1,224,128Z"), fill = SolidColor(Color.Black), fillAlpha = 0.2f)
            addPath(addPathNodes("M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm-8-80V80a8,8,0,0,1,16,0v56a8,8,0,0,1-16,0Zm20,36a12,12,0,1,1-12-12A12,12,0,0,1,140,172Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    /** Six dots in two columns (drag handle), drawn in the same 256 grid as the Phosphor set. */
    val DragIndicator: ImageVector by lazy {
        ImageVector.Builder("DragIndicator", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M73,60a15,15,0,1,0,30,0a15,15,0,1,0,-30,0ZM73,128a15,15,0,1,0,30,0a15,15,0,1,0,-30,0ZM73,196a15,15,0,1,0,30,0a15,15,0,1,0,-30,0ZM153,60a15,15,0,1,0,30,0a15,15,0,1,0,-30,0ZM153,128a15,15,0,1,0,30,0a15,15,0,1,0,-30,0ZM153,196a15,15,0,1,0,30,0a15,15,0,1,0,-30,0Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    /** "i" in a ring (info), drawn in the same 256 grid as the Phosphor set. */
    val Info: ImageVector by lazy {
        ImageVector.Builder("Info", 24.dp, 24.dp, 256f, 256f).apply {
            addPath(addPathNodes("M128,24a104,104,0,1,0,0,208a104,104,0,1,0,0,-208ZM128,40a88,88,0,1,1,0,176a88,88,0,1,1,0,-176Z"), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
            addPath(addPathNodes("M114,84a14,14,0,1,0,28,0a14,14,0,1,0,-28,0ZM116,116h24v68h-24Z"), fill = SolidColor(Color.Black))
        }.build()
    }

}
