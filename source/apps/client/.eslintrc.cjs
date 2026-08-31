/**
 * Lint rules for the client.
 *
 * Deliberately narrow: correctness rules that catch real defects, without
 * stylistic rules that would only add noise to a codebase this size.
 */
module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  parser: '@typescript-eslint/parser',
  parserOptions: { ecmaVersion: 2022, sourceType: 'module', ecmaFeatures: { jsx: true } },
  plugins: ['@typescript-eslint', 'react-hooks'],
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
  ],
  rules: {
    // The compiler already reports unused values; keep the lint signal on
    // things tsc does not catch.
    'no-unused-vars': 'off',
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    '@typescript-eslint/no-explicit-any': 'warn',
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    eqeqeq: ['error', 'smart'],
    'no-var': 'error',
    'prefer-const': 'error',
  },
  ignorePatterns: ['dist', 'node_modules', '*.config.ts', '*.config.js'],
};
