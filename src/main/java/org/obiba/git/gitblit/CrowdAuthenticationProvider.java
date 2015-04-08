package org.obiba.git.gitblit;

import com.atlassian.crowd.exception.ApplicationAccessDeniedException;
import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.CrowdException;
import com.atlassian.crowd.exception.InvalidTokenException;
import com.atlassian.crowd.model.user.User;
import com.gitblit.Constants.AccountType;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.UserModel;
import org.apache.wicket.protocol.http.WebRequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author pingunaut (Martin Spielmann)
 */
public class CrowdAuthenticationProvider extends UsernamePasswordAuthenticationProvider {

    public CrowdAuthenticationProvider() {
        super("org.obiba.git.gitblit.CrowdAuthenticationProvider");
    }

    private static final Logger log = LoggerFactory.getLogger(CrowdAuthenticationProvider.class);

    @Override
    public void setup() {

    }

    @Override
    public AccountType getAccountType() {
        return AccountType.EXTERNAL;
    }

    @Override
    public boolean supportsCredentialChanges() {
        return false;
    }

    @Override
    public boolean supportsDisplayNameChanges() {
        return false;
    }

    @Override
    public boolean supportsEmailAddressChanges() {
        return false;
    }

    @Override
    public boolean supportsTeamMembershipChanges() {
        return false;
    }

    @Override
    public void stop() {

    }

    private User doCrowdauthenticate(final String username, final String passwd) throws CrowdException,
            ApplicationPermissionException {
        WebRequestCycle requestCycle = (WebRequestCycle) WebRequestCycle.get();
        if (requestCycle != null) {
            // Try an SSO authentication
            HttpServletRequest request = requestCycle.getWebRequest().getHttpServletRequest();
            HttpServletResponse response = requestCycle.getWebResponse().getHttpServletResponse();
            try {
                return ExternalCrowdUserService.getCrowdAuthenticator().authenticate(request, response, username, passwd);
            } catch (InvalidTokenException e) {
                // ignore
            } catch (ApplicationAccessDeniedException e) {
                // ignore
            }
        }
        return ExternalCrowdUserService.getCrowdClient().authenticateUser(username, passwd);
    }

    @Override
    public UserModel authenticate(final String username, final char[] password) {
        try {
            User crowdUser = doCrowdauthenticate(username, new String(password));
            UserModel model = CrowdUtils.mapCrowdUserToModel(ExternalCrowdUserService.getCrowdClient(),
                    ExternalCrowdUserService.getRepositoryManager(), crowdUser);
            log.info("user {} successfully authenticated", username);
            return model;
        } catch (CrowdException e) {
            log.info("unable to authenticate user {}: {}", username, e.getMessage());
        } catch (ApplicationPermissionException e) {
            log.warn("unable to authenticate to crowd: {}", e.getMessage());
        }
        return null;
    }
}
