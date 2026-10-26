# Rule settings that differ by persona, set by judgement from each persona profile rather than
# fitted from data. fit/convert_params.py writes them into each persona's params file and notes
# them in its provenance.
#
# dm_burst_to_deliver: an Instagram or Snapchat 1:1 chat is held until the same person sends
# this many texts within 2 minutes. The nurse's social apps pull her in about half as much as
# the student's, and far more of her messages matter, so fewer texts get through.

PERSONA_POLICY: dict[str, dict[str, object]] = {
    "student_coder": {"dm_burst_to_deliver": 5},
    "night_shift_nurse": {"dm_burst_to_deliver": 3},
}
