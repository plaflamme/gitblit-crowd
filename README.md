GitBlit Crowd Integration
=========================

Integrates [Atlassian Crowd](http://www.atlassian.com/software/crowd/) with [GitBlit](http://gitblit.com).

Features
--------

* Authenticates users against Crowd
* SSO
* Uses Crowd groups as GitBlit teams
* Allows defining which Crowd group(s) have GitBlit admin privileges

Using it
--------
Download the jar file from [here](https://github.com/plaflamme/gitblit-crowd/downloads) and put it in GitBlit's classpath:

* GO: under the ``ext`` directory
* WAR: under ``WEB-INF/lib``

The edit GitBlit's configuration like so:

	realm.userService=org.obiba.git.gitblit.CrowdUserService

And configure it like so:

	# Where to load crowd.propeties from. Default is crowd.properties.
	crowd.properties=/path/to/your/crowd.properties
	# A file where this extension stores repository permissions. Default is perms.xml.
	crowd.permFile=perms.xml
	# Optional list of groups that will have GitBlit admin privileges. Default is empty.
	crowd.adminGroups=administrators gitblit-administrators

Refer to [Atlassian's documentation](http://confluence.atlassian.com/display/CROWD/The+crowd.properties+File) to create the ``crowd.properties`` file. By default, the extension will look for ``crowd.properties``.

SSO
---

Things work except when logging out from GitBlit.

Why can't I add/edit users and teams from GitBlit?
--------------------------------------------------

It's currently not implemented and probably never will. When using Crowd, it's probably a better idea to manage your users and groups there and then use them in GitBlit.
 
What's up with the perms.xml file?
----------------------------------

GitBlit requires that implementations of ``IUserService`` implement both authentication and authorization. Authentication is delegated to Crowd, but authorization isn't because Crowd doesn't know about GitBlit's repositories.

Thus, to implement authorization, this extension uses an extra file to store associations between repositories and users/teams. It was easier to write my own permission storage than leverage GitBlit's classes.

What's up with the weird version number?
----------------------------------------

We'll try to map to GitBlit versions up to the minor (x.y). Each of this extension's release within the same GitBlit minor version will have its own patch version (0.9-0, 0.9-0, 0.10-0, etc).
