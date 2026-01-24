package com.mfp.filemanager.utils

/**
 * Utility object for processing markdown text
 */
object MarkdownUtils {
    
    /**
     * Strips markdown formatting characters from text while preserving emojis and plain text.
     * Removes: #, *, _, `, ~, [], (), and other markdown syntax
     */
    fun stripMarkdown(text: String): String {
        var result = text
        
        // Remove headers (# ## ### etc.)
        result = result.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        
        // Remove bold/italic markers (**, *, __, _)
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")  // **bold**
        result = result.replace(Regex("__(.+?)__"), "$1")          // __bold__
        result = result.replace(Regex("\\*(.+?)\\*"), "$1")        // *italic*
        result = result.replace(Regex("_(.+?)_"), "$1")            // _italic_
        
        // Remove strikethrough (~~text~~)
        result = result.replace(Regex("~~(.+?)~~"), "$1")
        
        // Remove inline code (`code`)
        result = result.replace(Regex("`(.+?)`"), "$1")
        
        // Remove links but keep the text [text](url) -> text
        result = result.replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
        
        // Remove images ![alt](url)
        result = result.replace(Regex("!\\[.*?\\]\\(.+?\\)"), "")
        
        // Remove reference-style links [text][ref]
        result = result.replace(Regex("\\[(.+?)\\]\\[.+?\\]"), "$1")
        
        // Remove horizontal rules (---, ***, ___)
        result = result.replace(Regex("^([-*_]){3,}$", RegexOption.MULTILINE), "")
        
        // Remove blockquotes (> text)
        result = result.replace(Regex("^>\\s+", RegexOption.MULTILINE), "")
        
        // Remove list markers (-, *, +, 1., 2., etc.) at the start of lines
        result = result.replace(Regex("^[\\s]*[-*+]\\s+", RegexOption.MULTILINE), "")
        result = result.replace(Regex("^[\\s]*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        
        // Clean up extra whitespace
        result = result.replace(Regex("\\n{3,}"), "\n\n")  // Max 2 consecutive newlines
        result = result.trim()
        
        return result
    }
}
