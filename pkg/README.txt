cfucli - let someone you trust work on this machine's terminal
==================================================================

WHAT THIS IS

A window on this machine shows a terminal. Somebody you invite sees the same terminal
and can type into it. You are never locked out, you can watch every keystroke, and you
can stop them at any moment with one key.

It is meant for the times when explaining a problem over the phone is slower than
letting the other person just do it.


WHAT YOU NEED

Java 25 (JDK 25), installed and on the PATH. Nothing else. To check, open a command
prompt and type:

    java -version

Everything else is already inside the two .jar files here, including the window toolkit
and the terminal support. There is nothing to install, and nothing needs an
administrator. The folder can be copied anywhere; run install.cmd again afterwards so the
programs know where it went.


INSTALL IT FIRST

Double-click       install.cmd

It takes a couple of seconds. It points the two programs at the files beside them and
puts this folder on your PATH, so that "cfucli" works as a command from anywhere.
It needs no administrator and changes nothing else on the machine. It also puts an
cfucli shortcut on your desktop.

If you skip this step the window still works when double-clicked from this folder, but
the command line will not work from anywhere else.


START IT

Double-click the cfucli shortcut on your desktop, or cfucliapp.exe in this
folder. A window opens and offers to share this machine.


BEFORE THE FIRST TIME: CONNECT IT TO A RELAY

Out of the box this tool works only on this machine. To be reachable from somewhere
else it needs a relay - the meeting point the two ends dial out to, so that neither
needs an open port or a fixed address.

Whoever sent you this folder will send you three lines that look roughly like this:

    REDIS_URL = rediss://...
    UPSTASH_REDIS_REST_URL = https://...
    UPSTASH_REDIS_REST_TOKEN = ...

In the window, press  F5  (Relay setup), paste them into the box, press  F5  again to
save. The window tells you what it recognised as you paste, so you know before you save
whether it took. That is the whole setup, and it is remembered.

The line at the bottom of the launcher says whether a relay is configured. If it says
this machine is on its own, that step has not been done yet.


INVITING SOMEONE

1.  Press  F2  (Start sharing). A terminal window opens.
2.  Press  F4  (Copy invite). One string is now on your clipboard.
3.  Send that string to the person joining, however you normally message them.
4.  They paste it into their own copy of this tool and connect.
5.  A box appears here asking whether to allow them in. Nothing happens until you
    answer it. If you do not answer within a minute, it refuses by itself.

The invite works once, for that one session. Starting a new session makes a new one.


WHILE SOMEONE IS CONNECTED

They see the same terminal you see and can run commands in it. You can keep typing at
the same time - you are not locked out and you do not hand over control.

At any moment:

    F2    Lock them out.       They can still watch. Nothing they send will run.
    F3    Make it view only.   They can watch, but not type or run anything.
    F9    End the session.     Closing the window does the same thing.
    F12   Activity log.        Every command they ran, with the time and the result.

F12 is worth knowing about. The terminal shows you what the programs printed; the
activity log shows you what the other side actually asked for. If they are driving this
with an automated tool, that log is where you see each command as it is sent. The
bottom of the window tells you when there is something new in it.

The session also ends on its own: after 15 minutes with nothing happening, or after
4 hours regardless.


WHAT IS RECORDED, AND WHERE

Everything either side does is written to this machine, and it stays on this machine:

    %USERPROFILE%\cfucli\

Nothing is uploaded anywhere. The relay carries only the encrypted traffic between the
two ends while a session is live; it never holds a copy.

To read a recording back, use the command line in this folder:

    cfucli sessions              every session recorded here
    cfucli jobs --session <id>   every command run in one, with its result
    cfucli job <jobId>           one command in full, with its output

To delete recordings:

    cfucli forget --session <id>
    cfucli retain --days 7


IS IT SAFE

The honest version, rather than a reassurance.

Each session negotiates its own encryption key, and the invite's password authenticates
that negotiation rather than being the key itself. So the relay only ever sees
ciphertext - whoever runs the relay cannot read your session, and neither can anyone who
gets hold of the relay credentials afterwards.

What actually decides who gets in is you: the approval box on this machine. That is why
the invite can safely be one string rather than two.

What this tool does NOT protect you from is inviting the wrong person. Somebody
connected can run anything your account can run. Invite people you would hand the
keyboard to, end the session when the work is done, and use F2 or F3 the moment you want
to watch rather than assist.


IF SOMETHING GOES WRONG

The window starts but never offers a session id, or the other person cannot connect:

    Look in  %USERPROFILE%\cfucli\run\  - the files ending .log are
    plain text and open in Notepad.

    Check the bottom line of the launcher. If it says this machine is on its own, the
    relay setup above has not been done.

The other person says the invite does not work:

    Invites are per session. If you closed the window and started again, the old one is
    dead - press F4 for the new one.


THE TWO PROGRAMS IN THIS FOLDER

    install.cmd          run this once, first.
    cfucliapp.exe    the window. This is the one to double-click.
    cfucli.exe       the command line. Not needed to share this machine; it is here
                         so the relay can be changed without editing files by hand, and
                         so recordings can be read back. "cfucli guide" prints its
                         manual.
