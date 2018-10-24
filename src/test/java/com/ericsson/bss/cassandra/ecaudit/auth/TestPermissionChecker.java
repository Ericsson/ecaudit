//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Collections;
import java.util.EnumSet;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.UnauthorizedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestPermissionChecker
{
    @Mock
    private AuditAuthorizer authorizer;

    private PermissionChecker permissionChecker;

    @BeforeClass
    public static void beforeClass()
    {
        DatabaseDescriptor.clientInitialization(true);
    }

    @Before
    public void before()
    {
        DatabaseDescriptor.setAuthorizer(authorizer);
        permissionChecker = new PermissionChecker(authorizer);
    }

    @AfterClass
    public static void afterClass()
    {
        DatabaseDescriptor.clientInitialization(false);
    }

    @Test
    public void superUserCanChangeAllAttributes()
    {
        AuthenticatedUser performer = givenSuperUser();
        RoleResource grantee = givenNormalRole();
        RoleOptions options = givenAllOptions();
        permissionChecker.checkAlterRoleAccess(performer, grantee, options);
    }

    @Test
    public void normalUserCanChangeOwnPassword()
    {
        AuthenticatedUser performer = givenNormalUser();
        RoleResource grantee = givenNormalRole();
        RoleOptions options = givenPasswordOptions();
        permissionChecker.checkAlterRoleAccess(performer, grantee, options);
    }

    @Test
    public void normalUserCanAlterOtherOptionMap()
    {
        AuthenticatedUser performer = givenNormalUser();
        RoleResource grantee = givenOtherRole();
        RoleOptions options = givenOptionsOptions();
        permissionChecker.checkAlterRoleAccess(performer, grantee, options);
    }

    @Test(expected = UnauthorizedException.class)
    public void normalUserCanNotAlterOtherAllAttributes()
    {
        AuthenticatedUser performer = givenNormalUser();
        RoleResource grantee = givenOtherRole();
        RoleOptions options = givenAllOptions();
        permissionChecker.checkAlterRoleAccess(performer, grantee, options);
    }

    @Test
    public void trustedUserCanAlterOtherAllAttributes()
    {
        AuthenticatedUser performer = givenTrustedUser();
        RoleResource grantee = givenOtherRole();
        RoleOptions options = givenAllOptions();
        permissionChecker.checkAlterRoleAccess(performer, grantee, options);
    }

    private AuthenticatedUser givenSuperUser()
    {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.isSuper()).thenReturn(true);
        return user;
    }

    private AuthenticatedUser givenTrustedUser()
    {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.getName()).thenReturn("trusted");

        when(authorizer.realAuthorize(eq(user), any(RoleResource.class))).thenReturn(EnumSet.of(Permission.ALTER));

        return user;
    }

    private AuthenticatedUser givenNormalUser()
    {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.getName()).thenReturn("normal");
        return user;
    }

    private RoleResource givenNormalRole()
    {
        return RoleResource.role("normal");
    }

    private RoleResource givenOtherRole()
    {
        return RoleResource.role("other");
    }

    private RoleOptions givenAllOptions()
    {
        RoleOptions options = new RoleOptions();
        options.setOption(IRoleManager.Option.LOGIN, true);
        options.setOption(IRoleManager.Option.PASSWORD, "secret");
        options.setOption(IRoleManager.Option.SUPERUSER, true);
        options.setOption(IRoleManager.Option.OPTIONS, Collections.emptyMap());
        return options;
    }

    private RoleOptions givenPasswordOptions()
    {
        RoleOptions options = new RoleOptions();
        options.setOption(IRoleManager.Option.PASSWORD, "secret");
        return options;
    }

    private RoleOptions givenOptionsOptions()
    {
        RoleOptions options = new RoleOptions();
        options.setOption(IRoleManager.Option.OPTIONS, Collections.emptyMap());
        return options;
    }
}
