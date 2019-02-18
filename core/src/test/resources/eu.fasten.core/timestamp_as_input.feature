Feature: Resolve to the timestamp provided
  Construct a temporal dependency network according to the supplied timestamp

  Scenario: A user provides a timestamp
    Given The timestamp can be parsed as a Date object
    When I specify a common date format
    Then I should be given a temporal dependency network resolved to the specified timestamp



