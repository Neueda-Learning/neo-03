/**
 * The design system's single import surface.
 *
 *   import { AppShell, TopNav, DataTable, Badge, TONES } from './design-system';
 *
 * Import from here, never from a component file directly — the paths inside are
 * free to move, this list is not.
 */

/* Layout */
export { AppShell } from './components/layout/AppShell.jsx';
export { TopNav } from './components/layout/TopNav.jsx';
export { SideNav } from './components/layout/SideNav.jsx';
export { SideBrand } from './components/layout/SideBrand.jsx';
export { PageHeader } from './components/layout/PageHeader.jsx';
export { Toolbar } from './components/layout/Toolbar.jsx';
export { Split } from './components/layout/Split.jsx';
export { Grid } from './components/layout/Grid.jsx';
export { Stack } from './components/layout/Stack.jsx';
export { Section } from './components/layout/Section.jsx';

/* Core */
export { Button } from './components/core/Button.jsx';
export { Badge } from './components/core/Badge.jsx';
export { Chip, ChipGroup } from './components/core/Chip.jsx';
export { Tag } from './components/core/Tag.jsx';
export { Card } from './components/core/Card.jsx';
export { Divider } from './components/core/Divider.jsx';
export { Caption } from './components/core/Caption.jsx';

/* Data */
export { DataTable } from './components/data/DataTable.jsx';
export { KeyValue } from './components/data/KeyValue.jsx';
export { MetricTile } from './components/data/MetricTile.jsx';
export { BarChart } from './components/data/BarChart.jsx';
export { Timeline } from './components/data/Timeline.jsx';
export { StepTrail } from './components/data/StepTrail.jsx';

/* Forms */
export { Field } from './components/forms/Field.jsx';
export { TextInput } from './components/forms/TextInput.jsx';
export { Textarea } from './components/forms/Textarea.jsx';
export { Select } from './components/forms/Select.jsx';
export { SearchInput } from './components/forms/SearchInput.jsx';
export { Checkbox } from './components/forms/Checkbox.jsx';
export { Slider } from './components/forms/Slider.jsx';
export { FormGrid, FormActions } from './components/forms/FormGrid.jsx';

/* Feedback */
export { EmptyState } from './components/feedback/EmptyState.jsx';
export { Alert } from './components/feedback/Alert.jsx';
export { StatusPill } from './components/feedback/StatusPill.jsx';
export { StatusDot } from './components/feedback/StatusDot.jsx';
export { Spinner } from './components/feedback/Spinner.jsx';
export { CodeBlock } from './components/feedback/CodeBlock.jsx';

/* Overlay */
export { Modal } from './components/overlay/Modal.jsx';

/* Tones — the app's one job when adopting the system */
export { TONES, TONE_LIST, toTone, toneMapper } from './tones.js';
