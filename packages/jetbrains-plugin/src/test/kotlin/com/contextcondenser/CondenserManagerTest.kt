package com.contextcondenser

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Python adapter logic (called via subprocess).
 * These tests invoke python_adapter.py directly so they can run
 * in CI without a full IntelliJ sandbox.
 */
class PythonAdapterTest {

    private val adapterPath = "src/main/resources/scripts/python_adapter.py"

    private fun runAdapter(code: String, tier: Int = 2): Map<*, *> {
        val process = ProcessBuilder("python3", adapterPath, "--tier", tier.toString())
            .redirectErrorStream(false)
            .start()

        process.outputStream.bufferedWriter().use { it.write(code) }
        val stdout = process.inputStream.bufferedReader().readText()
        process.waitFor()

        return com.google.gson.Gson().fromJson(stdout, Map::class.java)
    }

    @Test
    fun `tier 2 strips function bodies`() {
        val source = """
            def calculate_tax(amount: float, rate: float) -> float:
                result = amount * rate
                if result < 0:
                    raise ValueError("Negative tax")
                return result
        """.trimIndent()

        val result = runAdapter(source, 2)
        val content = result["content"] as String

        assertTrue("Should preserve function signature", content.contains("def calculate_tax"))
        assertTrue("Should preserve type hints", content.contains("float"))
        assertFalse("Should strip implementation", content.contains("raise ValueError"))
        assertFalse("Should strip implementation", content.contains("result = amount"))
        assertTrue("Should contain ellipsis placeholder", content.contains("..."))
    }

    @Test
    fun `tier 2 preserves docstring summary line`() {
        val source = """
            def get_user(user_id: str) -> dict:
                \"\"\"Retrieve a user record by ID.
                
                This is a long docstring that should be truncated.
                More details here.
                \"\"\"
                return db.query(user_id)
        """.trimIndent()

        val result = runAdapter(source, 2)
        val content = result["content"] as String

        assertTrue("Should keep first docstring line",
            content.contains("Retrieve a user record by ID."))
        assertFalse("Should strip subsequent docstring lines",
            content.contains("long docstring that should"))
    }

    @Test
    fun `tier 1 strips comments only`() {
        val source = """
            # This is a comment
            x = 1  # inline comment
            y = x + 1
        """.trimIndent()

        val result = runAdapter(source, 1)
        val content = result["content"] as String

        assertFalse("Should strip comment lines", content.contains("This is a comment"))
        assertFalse("Should strip inline comments", content.contains("inline comment"))
        assertTrue("Should preserve code", content.contains("y = x + 1"))
    }

    @Test
    fun `token counts are positive and condensed is less than raw`() {
        val source = """
            class UserService:
                def __init__(self, db):
                    self.db = db
                    self.cache = {}
                    
                def get_user(self, uid: str) -> dict:
                    if uid in self.cache:
                        return self.cache[uid]
                    user = self.db.find(uid)
                    self.cache[uid] = user
                    return user
                    
                def delete_user(self, uid: str) -> bool:
                    if uid not in self.cache:
                        return False
                    del self.cache[uid]
                    self.db.delete(uid)
                    return True
        """.trimIndent()

        val result = runAdapter(source, 2)

        val rawTokens = (result["rawTokens"] as Double).toInt()
        val condensedTokens = (result["condensedTokens"] as Double).toInt()

        assertTrue("Raw tokens should be positive", rawTokens > 0)
        assertTrue("Condensed tokens should be positive", condensedTokens > 0)
        assertTrue("Condensed should be less than raw", condensedTokens < rawTokens)
    }

    @Test
    fun `empty input returns zero tokens`() {
        val result = runAdapter("")
        val rawTokens = (result["rawTokens"] as Double).toInt()
        val condensedTokens = (result["condensedTokens"] as Double).toInt()
        assertEquals(0, rawTokens)
        assertEquals(0, condensedTokens)
    }

    @Test
    fun `class definitions are preserved with method signatures`() {
        val source = """
            class AuthService:
                \"\"\"Handles user authentication.\"\"\"
                
                def login(self, email: str, password: str) -> bool:
                    user = self._find_user(email)
                    return self._verify(user, password)
                    
                def _find_user(self, email: str):
                    return self.db.users.find_one({"email": email})
        """.trimIndent()

        val result = runAdapter(source, 2)
        val content = result["content"] as String

        assertTrue("Should preserve class name", content.contains("class AuthService"))
        assertTrue("Should preserve login signature", content.contains("def login"))
        assertTrue("Should preserve type hints", content.contains("email: str"))
    }
}
