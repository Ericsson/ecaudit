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

        boolean needAlterPermission = false;
        if (options.getPassword().isPresent())
        {
            if (!performer.getName().equals(role.getRoleName()))
            {
                needAlterPermission = true;
            }
        }


        if (needAlterPermission || options.getLogin().isPresent() || options.getSuperuser().isPresent())
        {
            for (IResource resource : Resources.chain(role))
            {
                Set<Permission> grantedPermissions = getPermissions(performer, resource);
                if (grantedPermissions.contains(Permission.ALTER))
                {
                    return;
                }
            }

            throw new UnauthorizedException(String.format("User %s is not authorized to alter role %s",
                                                          performer.getName(), role.getRoleName()));
        }
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
