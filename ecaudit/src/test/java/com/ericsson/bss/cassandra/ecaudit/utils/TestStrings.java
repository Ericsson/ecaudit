/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.utils;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link Strings} class.
 */
public class TestStrings
{
    @Test
    public void testNonFrozenString()
    {
        String input = "<Kalle>><";
        String res = Strings.removeFrozenBrackets(input);
        assertThat(res).isEqualTo(input);
    }

    @Test
    public void testNestedFrozenTypes()
    {
        String input = "<map<frozen<int>, frozen<list<frozen<blob>>>, frozen<text>>>";
        String res = Strings.removeFrozenBrackets(input);
        assertThat(res).isEqualTo("<map<int, list<blob>, text>>");
    }

    @Test
    public void testFrozenWithoutBrackets()
    {
        String input = "frozen int <>";
        String res = Strings.removeFrozenBrackets(input);
        assertThat(res).isEqualTo(input);
    }

    @Test
    public void testFrozenWithExtraEndingBrackets()
    {
        String input = "frozen<list>>>";
        String res = Strings.removeFrozenBrackets(input);
        assertThat(res).isEqualTo("list>>");
    }

    @Test
    public void testOpenBrackets()
    {
        Strings.OpenBrackets openBrackets = new Strings.OpenBrackets();
        assertThat(openBrackets.brackets).isEmpty();

        // Given 2 start brackets containing 3 and 1 brackets respectively
        openBrackets.addStartBracket();
        openBrackets.increaseAll();
        openBrackets.increaseAll();
        openBrackets.addStartBracket();
        openBrackets.increaseAll();

        assertThat(openBrackets.brackets).hasSize(2);
        assertThat(openBrackets.brackets.getFirst().count).isEqualTo(3);
        assertThat(openBrackets.brackets.getLast().count).isEqualTo(1);

        // Removing last is ignored when bracket not closed
        assertThat(openBrackets.removeLastIfClosed()).isFalse();
        assertThat(openBrackets.brackets).hasSize(2);

        // Closing the last bracket and removing it
        openBrackets.decreaseAll();
        assertThat(openBrackets.removeLastIfClosed()).isTrue();
        assertThat(openBrackets.brackets).hasSize(1);

        openBrackets.decreaseAll();
        assertThat(openBrackets.removeLastIfClosed()).isFalse();

        // Closing also the first bracket
        openBrackets.decreaseAll();
        assertThat(openBrackets.removeLastIfClosed()).isTrue();
        assertThat(openBrackets.brackets).isEmpty();
    }
}
