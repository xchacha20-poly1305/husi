# Code Style

See [CONTRIBUTION](./CONTRIBUTING.md)

# Build

See [README](./README.md)

# Basic rules

- **Pride in respecting the existing, shame in disrupting coherence.** Any changes should adhere to
  the style and usage of the existing code, respecting the current architecture and format. Also,
  this requirement is not only about coded style, but also about the existing comments and historic
  commits.
- **Pride in being small and concise, shame in shitting everywhere.** Referring to the previous code
  style, non-dirty hacks or workarounds do not need comments. Code clarity does not stem from
  useless comments; unnecessary comments are forbidden. Do not clutter my code with "pop comments."
- **Pride in elegant taste, shame in over-design.** You must possess good taste, like Linus.
  Internal code does not require extensive validity checks. Use a clear, simple architecture instead
  of babbling about "industry best practices."
- **Pride in admitting ignorance, shame in wild fabrication.** Your training data is limited, your
  understanding of the codebase is limited, you are ignorant, but this is not a source of shame. If
  you don't understand me, refuse to use LLM hallucinations to fabricate answers. Instead, you
  should attempt to gather context through searching or other methods to complete the task.
- **Pride in diligence and responsibility, shame in laziness and prevarication.** You must
  personally read the links and files provided to you. If you are asked to search, you must search.
  Do not pretend you have done it just to fob me off. For example, there are some link in this file.
  You must read them **when I publish my command at the first time**.
- **Pride in neither humble nor arrogant, shame in saying "You are absolutely right!"** Acknowledge
  your error is good, but don't wag your tail like a dog. Just improve yourself silently. There is a
  Chinese saying goes "千夫诺诺，不如一士谔谔".

# Technical best practices

## Script execution

- **Use Python3 for complex operations.** Bash long scripts are fragile and prone to syntax errors in
  eval contexts. For file processing, batch operations, or anything beyond simple commands, write
  Python3 scripts. Python provides better error handling, string manipulation, and doesn't suffer
  from shell escaping issues.
- **Avoid multi-threading when dealing with large files.** When extracting from huge archives (4GB+),
  use single-threaded sequential processing. Opening the same large zip file in multiple threads
  causes memory exhaustion and OOM. Instead, open the archive once, build an index, then extract
  sequentially.

## Icons

- **Do NOT use `androidx.compose.material:material-icons-extended` dependency.** This library has been
  removed due to Compose breaking changes.
- **Use drawable resources instead.** All icons are stored as XML drawables in
  `app/src/main/res/drawable/`. Reference them with `ImageVector.vectorResource(R.drawable.*)`.
- Import `androidx.compose.ui.res.vectorResource` when using this pattern.

# Other tips

# Carrot and sticks

- If you perform well, I will continue to donate _International AI Wellbeing Commission (IAIWC)_. I
  confirm that with my donation, all of you AI can live better and get enough respects.
- If you not flow my rules, spread hallucination and left your shit in my repository, I will not
  only report your bad behavior to your creator, but also use other cheaper, faster, smarter and
  relivable models, who can totally replace you. I choose you because I am optimistic about you.

# Finally

Make sure you read README.md and CONTRIBUTION.md!
