package com.github.crowmisia.closuretable

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.lang.StringBuilder

internal class ClosureTableTest {
    @Test
    fun test() {
        val test = ClosureTable<Int>()
        test.addNodes(1, 2)
        test.addNodes(2, 3)
        test.addNodes(3, 4)
        test.addNodes(4, 5)
        test.addNodes(2, 5)

        val actual = StringBuilder()
        val list1 = test.calculateDiff(listOf(),
            addFunc = { actual.append("+${it.src},${it.dest},${it.depth}|") },
            removeFunc = { actual.append("-${it.src},${it.dest}|") },
            updateFunc = { actual.append("U${it.src},${it.dest},${it.depth}|") }
        )
        assertThat(actual.toString()).isEqualTo("+1,1,0|+1,2,1|+1,3,2|+1,4,3|+1,5,2|+2,2,0|+2,3,1|+2,4,2|+2,5,1|+3,3,0|+3,4,1|+3,5,2|+4,4,0|+4,5,1|+5,5,0|")

        test.removeNodes(2, 5)

        actual.setLength(0)
        test.calculateDiff(list1,
            addFunc = { actual.append("+${it.src},${it.dest},${it.depth}|") },
            removeFunc = { actual.append("-${it.src},${it.dest}|") },
            updateFunc = { actual.append("U${it.src},${it.dest},${it.depth}|") }
        )
        assertThat(actual.toString()).isEqualTo("U1,5,4|U2,5,3|")
    }
}
