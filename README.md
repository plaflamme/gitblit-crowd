GitBlit Crowd Integration
=========================

Integrates [Atlassian Crowd](http://www.atlassian.com/software/crowd/) with [GitBlit](http://gitblit.com).

[![Build Status](https://pingunaut.com/jenkins/buildStatus/icon?job=gitblit-crowd)](https://pingunaut.com/jenkins/job/gitblit-crowd/)

Features
--------

* Authenticates users against Crowd
* SSO
* Uses Crowd groups as GitBlit teams
* Allows defining which Crowd group(s) have GitBlit admin privileges

Using it
--------
Please refer to the project's page [here](http://plaflamme.github.com/gitblit-crowd/)

SSO
---

Crowd's single sign-on works fine (login and logout) with versions 1.0-0 and up.

Why can't I add/edit users and teams from GitBlit?
--------------------------------------------------

It's currently not implemented and probably never will. When using Crowd, it's probably a better idea to manage your users and groups there and then use them in GitBlit.
 
What's up with the perms.xml file?
----------------------------------

GitBlit requires that implementations of ``IUserService`` implement both authentication and authorization. Authentication is delegated to Crowd, but authorization isn't because Crowd doesn't know about GitBlit's repositories.

Thus, to implement authorization, this extension uses an extra file to store associations between repositories and users/teams. It was easier to write my own permission storage than leverage GitBlit's classes.

What's up with the weird version number?
----------------------------------------

We'll try to map to GitBlit versions up to the minor (x.y). Each of this extension's release within the same GitBlit minor version will have its own patch version (0.9-0, 0.9-1, 0.10-0, etc).
