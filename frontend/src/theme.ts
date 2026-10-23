import { createTheme } from '@mui/material'

export const theme = createTheme({
  palette: {
    primary: { main: '#0b7a75' },
    secondary: { main: '#13304f' },
    success: { main: '#2e9e6b' }, // AUTHENTIC
    warning: { main: '#f2a900' }, // SUSPECT
    error: { main: '#d6336c' }, // COUNTERFEIT
    background: { default: '#f4f7fa' },
  },
  shape: { borderRadius: 10 },
  typography: { fontFamily: '"Segoe UI", system-ui, sans-serif' },
})
