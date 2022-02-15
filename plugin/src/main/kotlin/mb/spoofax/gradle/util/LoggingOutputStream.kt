package mb.spoofax.gradle.util

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import java.io.IOException
import java.io.OutputStream
import java.util.regex.Pattern

class LoggingOutputStream(private val logger: Logger, private val level: LogLevel, vararg excludePatterns: String?) : OutputStream() {
  private val excludePatterns: Array<Pattern?>
  private var closed = false
  private var buffer: ByteArray
  private var count: Int

  init {
    this.excludePatterns = arrayOfNulls(excludePatterns.size)
    for(i in excludePatterns.indices) {
      this.excludePatterns[i] = Pattern.compile(excludePatterns[i], Pattern.DOTALL)
    }
    buffer = ByteArray(initialBufferLength)
    count = 0
  }

  override fun close() {
    flush()
    closed = true
  }

  @kotlin.jvm.Throws(IOException::class)
  override fun write(b: Int) {
    if(closed) {
      throw IOException("The stream has been closed")
    }
    when(b.toChar()) {
      '\n' -> {
        // Flush if writing last line separator.
        doFlush()
        return
      }
      '\r', 0.toChar() -> // Do not log carriage return and nulls.
        return
    }

    // Grow buffer if it is full.
    if(count == buffer.size) {
      val newBufLength = buffer.size + initialBufferLength
      val newBuf = ByteArray(newBufLength)
      System.arraycopy(buffer, 0, newBuf, 0, buffer.size)
      buffer = newBuf
    }
    buffer[count] = b.toByte()
    count++
  }

  override fun flush() {
    // Never manually flush, always require a \n to be written.
  }

  private fun doFlush() {
    try {
      val message = String(buffer, 0, count)
      for(pattern in excludePatterns) {
        if(pattern!!.matcher(message).matches()) {
          return
        }
      }
      logger.log(level, message)
    } finally {
      // Not resetting the buffer; assuming that if it grew that it will likely grow similarly again.
      count = 0
    }
  }

  companion object {
    private const val initialBufferLength = 2048
  }
}
