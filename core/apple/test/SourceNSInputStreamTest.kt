/*
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
 */

package kotlinx.io

import kotlinx.cinterop.*
import platform.Foundation.NSInputStream
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusNotOpen
import platform.Foundation.NSStreamStatusOpen
import platform.darwin.NSUIntegerVar
import platform.posix.uint8_tVar
import kotlin.test.*

@OptIn(UnsafeNumber::class)
class SourceNSInputStreamTest {
    @Test
    fun bufferInputStream() {
        val source = Buffer()
        source.writeString("abc")
        testInputStream(source.asNSInputStream())
    }

    @Test
    fun realSourceInputStream() {
        val source = Buffer()
        source.writeString("abc")
        testInputStream(RealSource(source).asNSInputStream())
    }

    private fun testInputStream(input: NSInputStream) {
        val byteArray = ByteArray(4)
        byteArray.usePinned {
            val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

            assertEquals(NSStreamStatusNotOpen, input.streamStatus)
            assertEquals(-1, input.read(cPtr, 4U))
            input.open()
            assertEquals(NSStreamStatusOpen, input.streamStatus)

            byteArray.fill(-5)
            assertEquals(3, input.read(cPtr, 4U))
            assertEquals("[97, 98, 99, -5]", byteArray.contentToString())

            byteArray.fill(-7)
            assertEquals(0, input.read(cPtr, 4U))
            assertEquals("[-7, -7, -7, -7]", byteArray.contentToString())
        }
    }

    @Test
    fun bufferInputStreamLongData() {
        val source = Buffer()
        source.writeString("a" + "b".repeat(Segment.SIZE * 2) + "c")
        testInputStreamLongData(source.asNSInputStream())
    }

    @Test
    fun realSourceInputStreamLongData() {
        val source = Buffer()
        source.writeString("a" + "b".repeat(Segment.SIZE * 2) + "c")
        testInputStreamLongData(RealSource(source).asNSInputStream())
    }

    private fun testInputStreamLongData(input: NSInputStream) {
        val lengthPlusOne = Segment.SIZE * 2 + 3
        val byteArray = ByteArray(lengthPlusOne)
        byteArray.usePinned {
            val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

            assertEquals(NSStreamStatusNotOpen, input.streamStatus)
            assertEquals(-1, input.read(cPtr, lengthPlusOne.convert()))
            input.open()
            assertEquals(NSStreamStatusOpen, input.streamStatus)

            byteArray.fill(-5)
            assertEquals(Segment.SIZE.convert(), input.read(cPtr, lengthPlusOne.convert()))
            assertEquals("[97${", 98".repeat(Segment.SIZE - 1)}${", -5".repeat(Segment.SIZE + 3)}]", byteArray.contentToString())

            byteArray.fill(-6)
            assertEquals(Segment.SIZE.convert(), input.read(cPtr, lengthPlusOne.convert()))
            assertEquals("[98${", 98".repeat(Segment.SIZE - 1)}${", -6".repeat(Segment.SIZE + 3)}]", byteArray.contentToString())

            byteArray.fill(-7)
            assertEquals(2, input.read(cPtr, lengthPlusOne.convert()))
            assertEquals("[98, 99${", -7".repeat(Segment.SIZE * 2 + 1)}]", byteArray.contentToString())

            byteArray.fill(-8)
            assertEquals(0, input.read(cPtr, lengthPlusOne.convert()))
            assertEquals("[-8${", -8".repeat(lengthPlusOne - 1)}]", byteArray.contentToString())
        }
    }

    @Test
    fun nsInputStreamClose() {
        val buffer = Buffer()
        buffer.writeString("abc")
        val source = RealSource(buffer)
        assertFalse(source.closed)

        val input = source.asNSInputStream()
        input.open()
        input.close()
        assertTrue(source.closed)
        assertEquals(NSStreamStatusClosed, input.streamStatus)

        val byteArray = ByteArray(4)
        byteArray.usePinned {
            val cPtr = it.addressOf(0).reinterpret<uint8_tVar>()

            byteArray.fill(-5)
            assertEquals(-1, input.read(cPtr, 4U))
            assertNotNull(input.streamError)
            assertEquals("Underlying source is closed.", input.streamError?.localizedDescription)
            assertEquals("[-5, -5, -5, -5]", byteArray.contentToString())
        }
    }
}
