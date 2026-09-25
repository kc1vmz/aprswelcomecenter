# Java change conventions

- For all changes, use imports and short class names instead of fully qualified class names in declarations and executable code, unless a naming conflict requires qualification. Check new and modified code for this before completing the work.
- When adding member variables to objects, add them to the relevant constructors and update constructor call sites. Preserve framework-required constructors, such as JPA no-argument constructors.
- During future work, explicitly tell the user when a new member variable has not been added to a relevant constructor, or when you encounter an existing omission in the objects being changed. Explain any necessary exception rather than silently leaving it out.
