package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Set;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.Resources;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.UnauthorizedException;

class PermissionChecker
{
    private IAuthorizer authorizer;

    PermissionChecker()
    {
        this.authorizer = null;
    }

    PermissionChecker(IAuthorizer authorizer)
    {
        this.authorizer = authorizer;
    }

    private IAuthorizer getAuthorizer()
    {
        if (authorizer == null)
        {
            return getAuthorizerSync();
        }

        return authorizer;
    }

    private synchronized IAuthorizer getAuthorizerSync()
    {
        if (authorizer == null)
        {
            authorizer = DatabaseDescriptor.getAuthorizer();
        }

        return authorizer;
    }

    void checkAlterRoleAccess(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    {
        if (performer.isSuper())
        {
            return;
        }

        if (isChangingPasswordOfOtherRole(performer, role, options) || isChangingRestrictedSettings(options))
        {
            if (!hasPermissionToAlterRole(performer, role))
            {
                throw new UnauthorizedException(String.format("User %s is not authorized to alter role %s",
                                                              performer.getName(), role.getRoleName()));
            }
        }
    }

    private boolean isChangingPasswordOfOtherRole(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    {
        if (options.getPassword().isPresent())
        {
            return !performer.getName().equals(role.getRoleName());
        }
        return false;
    }

    private boolean isChangingRestrictedSettings(RoleOptions options)
    {
        return options.getLogin().isPresent() || options.getSuperuser().isPresent();
    }

    private boolean hasPermissionToAlterRole(AuthenticatedUser performer, RoleResource roleResource)
    {
        for (IResource resource : Resources.chain(roleResource))
        {
            Set<Permission> grantedPermissions = getPermissions(performer, resource);
            if (grantedPermissions.contains(Permission.ALTER))
            {
                return true;
            }
        }
        return false;
    }

    private Set<Permission> getPermissions(AuthenticatedUser performer, IResource resource)
    {
        IAuthorizer currentAuthorizer = getAuthorizer();
        if (currentAuthorizer instanceof AuditAuthorizer)
        {
            return ((AuditAuthorizer) currentAuthorizer).realAuthorize(performer, resource);
        }
        else
        {
            return currentAuthorizer.authorize(performer, resource);
        }
    }
}
