# Neocities CLI

An attempt at a CLI tool for the [neocities](https://www.neocities.org/api) API. The official CLI tool only looks at file modify time and not SHA1 hash so it makes a _lot_ of redundant uploads when I use Jekyll to build my site.

## Building Ideas

- API Service that talks to Neocities API
- Local Service that plays with the filesystem
- A config bag that both take to decide if they want to upload/download/just report
- CLI options or interactive mode (jline?)

## Roadmap

- [ ] list all remote files
- [ ] list all local files
- [ ] config for rate limit on API interactions
- [ ] diff the remote files that have different SHA1 than local
- [ ] diff the local files that are not present in remote
- [ ] diff the remote files that are not present in local
- [ ] pretty-print diff status
- [ ] sync command for local -> remote
- [ ] sync command for remote -> local
- [ ] easy to distribute package. java thing or graalvm thing.
- [ ] Something about listing local files that are not allowed to be uploaded to neocites. They limit file extensions.

## Scratch

### Invoking the CLI ideas

```
neocli {command} {auth file} {directory}
```

- command
  - push the common usage, "upload" my site
  - pull the less common, make a backup of my site
  - report-push a diff of what would be pushed to remote
  - report-pull a diff of what would be downloaded from remote
- auth file
  - a simple file that holds username/password
- directory
  - the folder that the site lives to be uploaded or the folder to download to

### Reports

`report-push` should list the files that are different locally than remote

```
| File                      | Status  | Size  |
|---------------------------|---------|-------|
| index.html                | CHANGED | 200kb |
| blog/hello-world.html     | CHANGED | 75kb  |
| assets/styles.css         | CHANGED | 20kb  |
| blog/today-i-learned.html | NEW     | 89kb  |

To upload, run with "PUSH" command
```

Would report-pull be any different? I think if there is a file in the local folder that does not exist remote, then it would be deleted. Otherwise, similar thing. We would just download different files intead of upload different files. Similar situation.


