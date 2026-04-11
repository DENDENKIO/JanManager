package com.example.janmanager.util

object JanValidator {
    
    // Mapping of common OCR mistakes to potential digits
    private val OCR_SUBSTITUTIONS = mapOf(
        'I' to '1', 'l' to '1', '|' to '1', '!' to '1',
        'O' to '0', 'o' to '0', 'D' to '0', 'U' to '0',
        'B' to '8', 'S' to '5', 's' to '5', 'G' to '6', 'g' to '9', 'Z' to '2', 'z' to '2',
        'A' to '4', 'q' to '9', 'y' to '4'
    )

    /**
     * Checks if a string is a valid JAN-13 or JAN-8 code based on its checksum.
     */
    fun isValid(code: String): Boolean {
        if (code.length != 13 && code.length != 8) return false
        if (!code.all { it.isDigit() }) return false
        
        return if (code.length == 13) {
            calculateCheckDigit13(code.substring(0, 12)) == (code[12] - '0')
        } else {
            calculateCheckDigit8(code.substring(0, 7)) == (code[7] - '0')
        }
    }

    /**
     * Attempts to fix a code by replacing common OCR mistakes and checking the checksum.
     */
    fun tryFix(input: String): String? {
        // 1. Pre-cleaning: Remove anything that's not a digit or a known substitute char
        val cleanInput = input.filter { it.isDigit() || OCR_SUBSTITUTIONS.containsKey(it) }
        if (cleanInput.isEmpty()) return null

        // 2. Simple normalization: Try replacing all known substitutions at once
        val normalized = cleanInput.map { char ->
            if (char.isDigit()) char else OCR_SUBSTITUTIONS[char] ?: char
        }.joinToString("")
        
        // Return if normalized is valid and has correct length
        if (isValid(normalized)) return normalized

        // 3. Length specific logic: If it's close to 8 or 13, try to extract digits
        val onlyDigits = cleanInput.filter { it.isDigit() }
        if (isValid(onlyDigits)) return onlyDigits

        // 4. Brute force specific positions if it's 13 or 8 chars long
        if (cleanInput.length == 13 || cleanInput.length == 8) {
            val potential = cleanInput.toCharArray()
            val ambiguousIndices = mutableListOf<Int>()
            cleanInput.forEachIndexed { i, c ->
                if (OCR_SUBSTITUTIONS.containsKey(c)) ambiguousIndices.add(i)
            }

            if (ambiguousIndices.isNotEmpty() && ambiguousIndices.size <= 3) {
                val result = trySubstitutions(potential, ambiguousIndices, 0)
                if (result != null) return result
            }
            
            // 5. 1-bit Repair (Advanced): If it's digits only but checksum fails, try single digit changes
            if (normalized.length == 13 || normalized.length == 8) {
                val repair = tryOneBitRepair(normalized)
                if (repair != null) return repair
            }
        }
        
        return null
    }

    private fun tryOneBitRepair(digits: String): String? {
        val chars = digits.toCharArray()
        val validResults = mutableListOf<String>()
        
        for (i in chars.indices) {
            val original = chars[i]
            for (d in '0'..'9') {
                if (d == original) continue
                chars[i] = d
                val candidate = String(chars)
                if (isValid(candidate)) {
                    validResults.add(candidate)
                }
            }
            chars[i] = original // Backtrack
        }
        
        // If exactly one repair is found, we can be reasonably confident
        // For JAN codes, it's rare that two single-digit changes result in valid codes.
        return if (validResults.size == 1) validResults[0] else null
    }

    private fun trySubstitutions(chars: CharArray, indices: List<Int>, currentIndex: Int): String? {
        if (currentIndex == indices.size) {
            val s = String(chars)
            return if (isValid(s)) s else null
        }

        val idx = indices[currentIndex]
        val original = chars[idx]
        
        // Try original digit (if it was a digit)
        if (original.isDigit()) {
            val result = trySubstitutions(chars, indices, currentIndex + 1)
            if (result != null) return result
        }

        // Try the substitution
        val sub = OCR_SUBSTITUTIONS[original]
        if (sub != null) {
            chars[idx] = sub
            val result = trySubstitutions(chars, indices, currentIndex + 1)
            if (result != null) return result
            chars[idx] = original // backtrack
        }

        return null
    }

    fun calculateCheckDigit13(twelveDigits: String): Int {
        if (twelveDigits.length != 12) return -1
        var sumOdd = 0
        var sumEven = 0
        for (i in 0 until 12) {
            val digit = twelveDigits[i] - '0'
            if ((i + 1) % 2 == 0) {
                sumEven += digit
            } else {
                sumOdd += digit
            }
        }
        val total = sumOdd + (sumEven * 3)
        return (10 - (total % 10)) % 10
    }

    fun calculateCheckDigit8(sevenDigits: String): Int {
        if (sevenDigits.length != 7) return -1
        var sumOdd = 0
        var sumEven = 0
        for (i in 0 until 7) {
            val digit = sevenDigits[i] - '0'
            if ((i + 1) % 2 == 0) {
                sumEven += digit
            } else {
                sumOdd += digit
            }
        }
        val total = (sumOdd * 3) + sumEven
        return (10 - (total % 10)) % 10
    }
}
