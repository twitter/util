// Temporary workaround for https://github.com/pantsbuild/pants/pull/6673
// will be removed following the next pants release.
private final class PantsWorkaround {
  throw new IllegalStateException("workaround for https://github.com/pantsbuild/pants/pull/6673")
}
