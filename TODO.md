Your current task is to redesign the transition between single-pane and dual-pane layouts in the `DecksScreen.kt` file to follow Material Design 3 Expressive guidelines.

The goals are:
1. **Unified Surface Transition**: Treat the expansion as one continuous movement where the primary pane compresses and the second expands simultaneously.
2. **Expressive Motion**: Use high-damping spring curves and a 400-500ms duration.
3. **Reflowed Content**: Ensure items transitioning between layouts use `LookaheadScope` or `animateContentSize()` to avoid "janky" jumps.
4. **Staggered Entry**: New content in the secondary pane should fade in with a slight stagger.

I will be looking for:
- `DecksScreen.kt` to identify the current state management and layout logic (e.g., `ListDetailPaneScaffold` or custom `AnimatedContent`/`Modifier.graphicsLayer` implementations).
- The navigation/state logic that determines if the UI is in "Mobile" (single pane) or "Desktop" (dual pane) mode.
- The Compose UI code responsible for rendering the grid and the transition.

I will start by exploring the project structure and locating the relevant files.