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


import java.util.Deque;
import java.util.LinkedList;

public final class Strings
{
    static final char FROZEN_START = '#';

    private Strings()
    {
        // Utility class
    }

    /**
     * Remove the "frozen brackets" from a type definition string. The method handles nested frozen types.
     * <p>
     * The input {@code "map<int, frozen<list<blob>>>"} will give {@code "map<int, list<blob>>"} as a result.
     *
     * @param input type string
     * @return an "unfrozen" string representation of the type
     */
    public static String removeFrozenBrackets(String input)
    {
        String unfrozen = input.replaceAll("frozen<", String.valueOf(FROZEN_START));
        if (input.length() == unfrozen.length())
        {
            return input; // input is not frozen
        }
        StringBuilder out = new StringBuilder();
        OpenBrackets openBrackets = new OpenBrackets();
        for (char c : unfrozen.toCharArray())
        {
            switch (c)
            {
                case FROZEN_START:
                    openBrackets.addStartBracket();
                    openBrackets.increaseAll();
                    break;
                case '<':
                    openBrackets.increaseAll();
                    out.append(c);
                    break;
                case '>':
                    openBrackets.decreaseAll();
                    if (!openBrackets.removeLastIfClosed())
                    {
                        out.append(c);
                    }
                    break;
                default:
                    out.append(c);
                    break;
            }
        }
        return out.toString();
    }

    static class OpenBrackets
    {
        Deque<BracketCount> brackets = new LinkedList<>();

        void addStartBracket()
        {
            brackets.addLast(new BracketCount());
        }

        void increaseAll()
        {
            brackets.forEach(BracketCount::increase);
        }

        void decreaseAll()
        {
            brackets.forEach(BracketCount::decrease);
        }

        boolean removeLastIfClosed()
        {
            boolean isLastClosed = !brackets.isEmpty() && brackets.getLast().isClosed();
            if (isLastClosed)
            {
                brackets.removeLast();
            }
            return isLastClosed;
        }
    }

    static class BracketCount
    {
        int count = 0;

        void increase()
        {
            count++;
        }

        void decrease()
        {
            count--;
        }

        boolean isClosed()
        {
            return count == 0;
        }
    }
}
