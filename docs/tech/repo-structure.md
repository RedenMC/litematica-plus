# RVC git repo structure

Each RVC project is a git repo.

## Directory structure

- local.json
- index.json
- index.nbt
- READMD.md
- .gitignore
- `<regionname>.nbt`

## Files

local.json:

Master Origin, this is the origin that all subregion origins are relative to.
This is never changed even the subregion size changes to keep the subregion origins stable.

index.json:

Records the subregion definitions and other metadata.

**Subregion bounding box changes**:
Use this file to detect any bbox changing.
If bbox changed, when merging, fir prompt the user to choose which bbox to use, and then update
the index.nbt block coordinates accordingly.

index.nbt, *.nbt:

subregions. stored im vanilla structure block format.

