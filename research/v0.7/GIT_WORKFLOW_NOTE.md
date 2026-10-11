# Optional CI setup on a separate QA branch

These source files are intended to live under `qa/realscale/geocore/` in the
SWARM-MOBS GitHub QA branch. They are kept separate from Minecraft's mod source;
no new mod is registered and main remains unchanged.

The GitHub Actions workflow should run `bash qa/realscale/geocore/build.sh`
from the `qa/realscale/geocore` directory (the script changes into its own folder).
