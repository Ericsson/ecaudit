/*
 * Copyright 2020 Telefonaktiebolaget LM Ericsson
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

import org.apache.cassandra.auth.AuthenticatedUser;

public final class AuthenticatedUserUtil
{
   private  AuthenticatedUserUtil()
   {
       // Utility class
   }

    /**
     * Creates an AuthenticatedUser from the given string.
     * Makes sure the correct constants for system/anonymous users are used. This ensures that the
     * {@link AuthenticatedUser#isSystem()} and {@link AuthenticatedUser#isAnonymous()} methods work correctly.
     *
     * @param user the user
     * @return the created authenticated user
     */
   public static AuthenticatedUser createFromString(String user)
   {
       switch (user)
       {
           case AuthenticatedUser.SYSTEM_USERNAME:
               return AuthenticatedUser.SYSTEM_USER;
           case AuthenticatedUser.ANONYMOUS_USERNAME:
               return AuthenticatedUser.ANONYMOUS_USER;
           default:
               return new AuthenticatedUser(user);
       }
   }
}
