import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';

// The design system first, this app's few screen-specific rules second — so a
// diff shows exactly where we have overridden the system, and how little.
import './design-system/styles.css';
import './styles.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
