package org.obiba.git.gitblit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccountType;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TeamModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PersistableTeamModel extends TeamModel {

    private static final long serialVersionUID = 4933572963838755763L;

    @JsonIgnore
    @Override
    public List<RegistrantAccessPermission> getRepositoryPermissions() {
        return super.getRepositoryPermissions();
    }

    public PersistableTeamModel() {
        super("");
    }

    public PersistableTeamModel(final String name) {
        super(name);
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isCanAdmin() {
        return canAdmin;
    }

    public void setCanAdmin(final boolean canAdmin) {
        this.canAdmin = canAdmin;
    }

    public boolean isCanFork() {
        return canFork;
    }

    public void setCanFork(final boolean canFork) {
        this.canFork = canFork;
    }

    public boolean isCanCreate() {
        return canCreate;
    }

    public void setCanCreate(final boolean canCreate) {
        this.canCreate = canCreate;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(final AccountType accountType) {
        this.accountType = accountType;
    }

    public Set<String> getUsers() {
        return users;
    }

    public Map<String, AccessPermission> getPermissions() {
        return permissions;
    }
}
