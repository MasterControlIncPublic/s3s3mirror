s3s3mirror
==========

A utility for mirroring content between S3 buckets and/or local directories. 

Designed to be lightning-fast and highly concurrent, with modest CPU and memory requirements.

An object will be copied if and only if at least one of the following holds true:

* The object does not exist in the destination bucket.
* The "sync strategy" triggers (by default uses the Etag sync strategy)
    * Etag Strategy: If the size or Etags don't match between the source and destination bucket.
    * Size Strategy: If the sizes don't match between the source and destination bucket.
    * Size and Last Modified Strategy: If the source and destination objects have a different size, or the source bucket object has a more recent last modified date.
    * SHA256 Strategy: Compares a file's SHA256 hash vs. a stored hash as custom metadata on the S3 objects. 

When copying, the source metadata and ACLs are also copied to the destination object.

### *New in 2.0: Local filesystem support*

The latest s3s3mirror permits the source or destination to be a local filesystem path. Some caveats when copying to/from your local system: 

* The ETag comparison strategy is not supported since local files do not have ETags, and even if they did, they wouldn't match with what S3 generates.
* When copying from S3 to your local system, metadata and ACLs are not copied (what would they mean anyway on your local system?)
* When copying from your local system to S3, no metadata or ACLs are defined for the S3 object (they will be subject to whatever default IAM policies you have set for the bucket).

### Motivation

I started with "s3cmd sync" but found that with buckets containing many thousands of objects, it was incredibly slow
to start and consumed *massive* amounts of memory. So I designed s3s3mirror to start copying immediately with an intelligently
chosen "chunk size" and to operate in a highly-threaded, streaming fashion, so memory requirements are much lower.

Running with 100 threads, I found the gating factor to be *how fast I could list items from the source bucket* (!?!)
Which makes me wonder if there is any way to do this faster. I'm sure there must be, but this is pretty damn fast.

### AWS Credentials

* s3s3mirror will first look for credentials in your system environment. If variables named AWS\_ACCESS\_KEY\_ID and AWS\_SECRET\_ACCESS\_KEY are defined, then these will be used.
* Next, it checks for a ~/.s3cfg file (which you might have for using s3cmd). If present, the access key and secret key are read from there.
* If neither of the above is found, it will error out and refuse to run.

### System Requirements

* Java 8 or higher

### Building

    mvn package

Note that s3s3mirror now has a prebuilt jar checked in to github, so you'll only need to do this if you've been playing with the source code.
The above command requires that Maven 3 is installed.

### License

s3s3mirror is available under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0).

### Usage

    s3s3mirror.sh [options] <source-bucket>[/src-prefix/path/...] <destination-bucket>[/dest-prefix/path/...]

If a bucket name starts with `/` or `./` it will be interpreted as a directory path on the local system. On Windows, use `\` or `.\`

If the source is the special value `.-` then the list of files to upload to S3 will be read from stdin, one file per line.
If the files read from stdin are not absolute paths, they will be considered as relative to the current directory.
If the files read in *are* absolute paths, you probably don't want this: the uploaded file on S3 will be stored under
`destination-bucket/dest-prefix/absolute-path-to-file` which is probably not what you want. Strip off absolute-path prefixes from the
files before feeding them to s3s3mirror on stdin.

### Versions

The 1.x branch (currently master) has been in use by the most number of people and is the most battle tested.

The 2.x branch supports copying between S3 and any local filesystem. It has seen heavy use and performs well, but is not as widely used as the 1.x branch.

**In the near future, the 1.x branch will offshoot from master, and the 2.x branch will be merged into master.** There are a handful of features
on the 1.x branch that have not yet been ported to 2.x. If you can live without them, I encourage you to use the 2.x branch. If you really need them,
I encourage you to port them to the 2.x branch, if you have the ability.

### Options

    -c (--ctime) N           : Only copy objects whose Last-Modified date is younger than this many days
                               For other time units, use these suffixes: y (years), M (months), d (days), w (weeks),
                                                                         h (hours), m (minutes), s (seconds)
    -m (--max-connections) N  : Maximum number of connections to S3 (default 100)
    -n (--dry-run)            : Do not actually do anything, but show what would be done (default false)
    -r (--max-retries) N      : Maximum number of retries for S3 requests (default 5)
    -p (--prefix) VAL         : Only copy objects whose keys start with this prefix
    -d (--dest-prefix) VAL    : Destination prefix (replacing the one specified in --prefix, if any)
    -R (--regex) VAL          : Only copy objects whose keys match this regular expression. Beware shell escaping mistakes.
    -S (--sync-strategy)      : Choose the syncing strategy to be used to determine which objects should be copied.
                                AUTO uses SIZE_SHA256 when the local file system is part of the copy and SIZE_ETAG when doing S3 to S3 copies. (default: AUTO)
                                [SIZE | SIZE_ETAG | SIZE_SHA256 | 
                                        SIZE_LAST_MODIFIED | AUTO]
    -e (--endpoint) VAL       : AWS endpoint to use (or set AWS_ENDPOINT in your environment)
    -X (--delete-removed)     : Delete objects from the destination bucket if they do not exist in the source bucket
    -t (--max-threads) N      : Maximum number of threads (default 100)
    -v (--verbose)            : Verbose output (default false)
    -z (--proxy) VAL          : host:port of proxy server to use.
                                Defaults to proxy_host and proxy_port defined in ~/.s3cfg,
                                or no proxy if these values are not found in ~/.s3cfg
    -u (--upload-part-size) N : The upload size (in bytes) of each part uploaded as part of a multipart request
                                for files that are greater than the max allowed file size of 5368709120 bytes (5 GB)
                                Defaults to 4294967296 bytes (4 GB)
    -C (--cross-account-copy) : Copy across AWS accounts. Only Resource-based policies are supported (as
                                specified by AWS documentation) for cross account copying
                                Default is false (copying within same account, preserving ACLs across copies)
                                If this option is active, the owner of the destination bucket will receive full control                                
    -s (--ssl)                : Use SSL for all S3 api operations (default false)
    -E (--server-side-encryption) : Enable AWS managed server-side encryption (default false)
    -l (--storage-class)      : S3 storage class "Standard" or "ReducedRedundancy" (default Standard)


### Examples

Copy everything from a bucket named "source" to another bucket named "dest"

    s3s3mirror.sh source dest

Copy everything from "source" to "dest", but only copy objects created or modified within the past week

    s3s3mirror.sh -c 7 source dest
    s3s3mirror.sh -c 7d source dest
    s3s3mirror.sh -c 1w source dest
    s3s3mirror.sh --ctime 1w source dest

Copy everything from "source/foo" to "dest/bar"

    s3s3mirror.sh source/foo dest/bar
    s3s3mirror.sh -p foo -d bar source dest

Copy everything from "source/foo" to "dest/bar" and delete anything in "dest/bar" that does not exist in "source/foo"

    s3s3mirror.sh -X source/foo dest/bar
    s3s3mirror.sh --delete-removed source/foo dest/bar
    s3s3mirror.sh -p foo -d bar -X source dest
    s3s3mirror.sh -p foo -d bar --delete-removed source dest

Copy within a single bucket -- copy everything from "source/foo" to "source/bar"

    s3s3mirror.sh source/foo source/bar
    s3s3mirror.sh -p foo -d bar source source

BAD IDEA: If copying within a single bucket, do *not* put the destination below the source

    s3s3mirror.sh source/foo source/foo/subfolder
    s3s3mirror.sh -p foo -d foo/subfolder source source
*This might cause recursion and raise your AWS bill unnecessarily*

MORE BAD IDEAS: Use caution with the `-X` / `--delete-removed` flag. If run from the wrong directory, you will likely *delete everything* on the destination.

For example, if both /some/empty/dir and some-empty-bucket are empty:

    s3s3mirror.sh -X /some/empty/dir some-bucket-with-stuff  # delete everything in some-bucket-with-stuff
    s3s3mirror.sh -X some-empty-bucket ./                    # delete everything in your current directory
    s3s3mirror.sh -X some-empty-bucket /                     # DO NOT DO THIS! It will delete everything on your local system!

When in doubt, use the `-n` / `--dry-run` option first to ensure that s3s3mirror will behave as you expect.

###### If you've enjoyed using s3s3mirror and are looking for a warm-fuzzy feeling, consider dropping a little somethin' into my [tip jar](https://cobbzilla.org/tipjar.html)
