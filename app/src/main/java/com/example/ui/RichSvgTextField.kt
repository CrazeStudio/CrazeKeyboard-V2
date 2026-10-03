package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.util.regex.Pattern

@Composable
fun RichSvgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val svgPattern = remember { Pattern.compile("(<svg.*?>.*?</svg>)", Pattern.DOTALL) }
    
    // Parse SVG tags and create the AnnotatedString with placeholders
    val transformedData = remember(value) {
        val matcher = svgPattern.matcher(value)
        val builder = AnnotatedString.Builder()
        val inlineContentMap = mutableMapOf<String, InlineTextContent>()
        
        val offsetMap = mutableListOf<Int>()
        val originalToTransformed = mutableListOf<Int>()
        val transformedToOriginal = mutableListOf<Int>()
        
        var lastEnd = 0
        var svgCount = 0
        var transformedIndex = 0
        
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val svgString = matcher.group(1) ?: ""
            
            // Text before SVG
            val textBefore = value.substring(lastEnd, start)
            builder.append(textBefore)
            // Efficient mapping for text chunks
            for (i in lastEnd until start) {
                originalToTransformed.add(transformedIndex)
                transformedToOriginal.add(i)
                transformedIndex++
            }
            
            // SVG placeholder
            val id = "svg_$svgCount"
            svgCount++
            
            builder.pushStyle(SpanStyle(fontSize = 100.sp))
            builder.appendInlineContent(id, "\uFFFC")
            builder.pop()
            
            inlineContentMap[id] = InlineTextContent(
                Placeholder(
                    width = 100.sp,
                    height = 100.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom
                )
            ) {
                val svgBytes = svgString.toByteArray(Charsets.UTF_8)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(svgBytes)
                        .decoderFactory(SvgDecoder.Factory())
                        .build(),
                    contentDescription = "SVG",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
            
            // Map the entire <svg> string to the single \uFFFC character
            for (i in start until end) {
                originalToTransformed.add(transformedIndex)
            }
            transformedToOriginal.add(start) // \uFFFC maps to the start of <svg>
            transformedIndex++
            
            lastEnd = end
        }
        
        // Remaining text
        val textAfter = value.substring(lastEnd)
        builder.append(textAfter)
        for (i in lastEnd until value.length) {
            originalToTransformed.add(transformedIndex)
            transformedToOriginal.add(i)
            transformedIndex++
        }
        
        // Add one more for the cursor at the end
        originalToTransformed.add(transformedIndex)
        transformedToOriginal.add(value.length)
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (originalToTransformed.isEmpty()) return 0
                val safeOffset = offset.coerceIn(0, originalToTransformed.size - 1)
                return originalToTransformed[safeOffset]
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (transformedToOriginal.isEmpty()) return 0
                val safeOffset = offset.coerceIn(0, transformedToOriginal.size - 1)
                return transformedToOriginal[safeOffset]
            }
        }
        
        Triple(builder.toAnnotatedString(), inlineContentMap, offsetMapping)
    }
    
    val annotatedString = transformedData.first
    val inlineContentMap = transformedData.second
    val offsetMapping = transformedData.third
    
    val textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        if (value.isEmpty()) {
            Text("Tap to start typing...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        
        // Render the Rich Text
        Text(
            text = annotatedString,
            inlineContent = inlineContentMap,
            style = textStyle,
            modifier = Modifier.fillMaxSize()
        )
        
        // The actual editor, fully transparent text
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = textStyle.copy(color = Color.Transparent),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = { _ -> 
                TransformedText(annotatedString, offsetMapping) 
            }
        )
    }
}
