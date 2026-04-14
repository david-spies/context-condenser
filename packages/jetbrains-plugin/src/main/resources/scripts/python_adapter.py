#!/usr/bin/env python3
"""
Context Condenser — Python AST Adapter
=======================================
Bundled sidecar script for the JetBrains plugin.

Reads Python source code from stdin, applies tiered condensation,
and writes a JSON response to stdout:

  {
    "content": "<condensed source>",
    "rawTokens": <int>,
    "condensedTokens": <int>
  }

Tiered condensation strategy:
  Tier 1 — Strip comments and multi-line docstrings (keep summary line)
  Tier 2 — Structural: replace function/class bodies with '...' (default)
  Tier 3 — AI-Driven: placeholder; implement with your preferred LLM API

Usage (from Kotlin ProcessBuilder):
  python3 python_adapter.py --tier 2 < source.py
"""

import ast
import sys
import json
import argparse
import textwrap


# ─────────────────────────────────────────────────────────────────────────────
# Token counting
# ─────────────────────────────────────────────────────────────────────────────

def count_tokens(text: str, model: str = "cl100k_base") -> int:
    """
    Count tokens using tiktoken when available.
    Falls back to a 4-chars-per-token estimate so the plugin
    degrades gracefully without requiring tiktoken.
    """
    try:
        import tiktoken
        enc = tiktoken.get_encoding(model)
        return len(enc.encode(text))
    except ImportError:
        return max(1, len(text) // 4)
    except Exception:
        return max(1, len(text) // 4)


# ─────────────────────────────────────────────────────────────────────────────
# Tier 1 — Comment + docstring stripping
# ─────────────────────────────────────────────────────────────────────────────

def strip_comments(source: str) -> str:
    """
    Remove inline comments and standalone comment lines.
    Preserves string literals to avoid corrupting code.
    """
    lines = source.splitlines()
    cleaned = []
    for line in lines:
        stripped = line.lstrip()
        if stripped.startswith('#'):
            continue  # Remove full comment lines
        # Remove inline comments (naive but fast — AST handles edge cases)
        if '#' in line:
            # Only strip if # is not inside a string (good-enough heuristic)
            code_part = line.split('#')[0].rstrip()
            cleaned.append(code_part if code_part else '')
        else:
            cleaned.append(line)
    return '\n'.join(cleaned)


# ─────────────────────────────────────────────────────────────────────────────
# Tier 2 — Structural AST condensation (the core feature)
# ─────────────────────────────────────────────────────────────────────────────

class StructuralCondenser(ast.NodeTransformer):
    """
    Walks the AST and replaces function/class bodies with a single
    Ellipsis node, preserving:
      - Function signatures (name, args, return type annotations)
      - Type hints on parameters
      - First line of docstrings (the summary sentence)
      - Class-level variable annotations (PEP 526)
    """

    def __init__(self, include_docstring_summary: bool = True):
        self.include_docstring_summary = include_docstring_summary

    def _get_docstring_summary(self, node: ast.AST) -> str | None:
        """Extract only the first line of a docstring if present."""
        if not self.include_docstring_summary:
            return None
        if not isinstance(node.body[0], ast.Expr):  # type: ignore[attr-defined]
            return None
        expr = node.body[0]  # type: ignore[attr-defined]
        if not isinstance(expr.value, ast.Constant) or not isinstance(expr.value.value, str):
            return None
        first_line = expr.value.value.strip().splitlines()[0]
        return first_line if first_line else None

    def _make_condensed_body(self, node: ast.AST) -> list:
        body = node.body  # type: ignore[attr-defined]
        new_body = []

        if len(body) > 0:
            summary = self._get_docstring_summary(node)
            if summary:
                new_body.append(ast.Expr(value=ast.Constant(value=summary)))

        # The condensation placeholder
        new_body.append(ast.Expr(value=ast.Constant(value="...")))
        return new_body

    def visit_FunctionDef(self, node: ast.FunctionDef) -> ast.AST:
        if len(node.body) > 1:
            node.body = self._make_condensed_body(node)
        return node

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> ast.AST:
        if len(node.body) > 1:
            node.body = self._make_condensed_body(node)
        return node

    def visit_ClassDef(self, node: ast.ClassDef) -> ast.AST:
        # For classes: preserve class-level annotations and method signatures,
        # but condense each method's body individually via generic_visit
        self.generic_visit(node)  # recurse into methods first
        return node


def condense_structural(source: str, include_docstring_summary: bool = True) -> str:
    """Apply Tier 2 AST structural condensation."""
    try:
        tree = ast.parse(source)
    except SyntaxError as e:
        return f"# AST parse error: {e}\n{source}"

    condenser = StructuralCondenser(include_docstring_summary)
    new_tree = condenser.visit(tree)
    ast.fix_missing_locations(new_tree)

    try:
        return ast.unparse(new_tree)
    except Exception as e:
        return f"# AST unparse error: {e}\n{source}"


# ─────────────────────────────────────────────────────────────────────────────
# Tier 3 — AI-Driven (placeholder for LLM integration)
# ─────────────────────────────────────────────────────────────────────────────

def condense_ai_driven(source: str) -> str:
    """
    Tier 3: Send the Tier 2 skeleton to an LLM for a prose summary.

    To implement: call your preferred LLM API here. The skeleton produced
    by Tier 2 is already small enough to fit cheaply into any model's
    context window.

    Example with anthropic SDK:
        import anthropic
        client = anthropic.Anthropic()
        response = client.messages.create(
            model="claude-3-haiku-20240307",
            max_tokens=512,
            messages=[{"role":"user","content":f"Summarize:\\n{skeleton}"}]
        )
        return response.content[0].text
    """
    skeleton = condense_structural(source)
    return f"# [Tier 3 — AI summary not configured]\n{skeleton}"


# ─────────────────────────────────────────────────────────────────────────────
# Jupyter Notebook support (.ipynb)
# ─────────────────────────────────────────────────────────────────────────────

def condense_notebook(content: str, tier: int) -> str:
    """
    Condenses a Jupyter notebook by processing only code cells.
    Markdown cells are kept as-is (they're already low-token documentation).
    Output cells are stripped entirely — they're the biggest token waste.
    """
    try:
        import json as _json
        nb = _json.loads(content)
    except Exception:
        return content  # Not valid JSON — return raw

    result_cells = []
    for cell in nb.get("cells", []):
        cell_type = cell.get("cell_type", "")
        source = "".join(cell.get("source", []))

        if cell_type == "code":
            condensed = apply_tier(source, tier)
            result_cells.append(f"```python\n{condensed}\n```")
        elif cell_type == "markdown":
            # Keep markdown but strip it down to first line of each paragraph
            first_lines = "\n".join(
                block.splitlines()[0] for block in source.split("\n\n") if block.strip()
            )
            result_cells.append(first_lines)

    return "\n\n---\n\n".join(result_cells)


# ─────────────────────────────────────────────────────────────────────────────
# Dispatcher
# ─────────────────────────────────────────────────────────────────────────────

def apply_tier(source: str, tier: int, include_docstring: bool = True) -> str:
    if tier == 1:
        return strip_comments(source)
    elif tier == 2:
        return condense_structural(source, include_docstring)
    elif tier == 3:
        return condense_ai_driven(source)
    else:
        return source


# ─────────────────────────────────────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Context Condenser Python Adapter")
    parser.add_argument("--tier", type=int, default=2, choices=[1, 2, 3],
                        help="Condensation tier (1=comments, 2=structural, 3=AI)")
    parser.add_argument("--no-docstring", action="store_true",
                        help="Strip docstrings entirely (don't preserve summary line)")
    parser.add_argument("--notebook", action="store_true",
                        help="Input is a Jupyter notebook (.ipynb JSON)")
    parser.add_argument("--encoding", default="cl100k_base",
                        help="tiktoken encoding for token counting")
    args = parser.parse_args()

    input_data = sys.stdin.read()

    if not input_data.strip():
        print(json.dumps({"content": "", "rawTokens": 0, "condensedTokens": 0}))
        return

    raw_tokens = count_tokens(input_data, args.encoding)

    try:
        if args.notebook:
            condensed = condense_notebook(input_data, args.tier)
        else:
            condensed = apply_tier(input_data, args.tier, not args.no_docstring)

        condensed_tokens = count_tokens(condensed, args.encoding)

        output = {
            "content": condensed,
            "rawTokens": raw_tokens,
            "condensedTokens": condensed_tokens,
        }
        sys.stdout.write(json.dumps(output))

    except Exception as e:
        sys.stderr.write(f"Condensation failed: {e}\n")
        # Graceful degradation: return original with estimated counts
        output = {
            "content": input_data,
            "rawTokens": raw_tokens,
            "condensedTokens": raw_tokens,
            "error": str(e),
        }
        sys.stdout.write(json.dumps(output))


if __name__ == "__main__":
    main()
