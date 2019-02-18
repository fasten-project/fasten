Feature: Use current time if a timestamp is not provided
  If a timestamp is not provided, resolve to a dependency network of current time.

  Scenario: A user provides an empty timestamp
    Given A timestamp is not provided in the query
    When I only specify a package and its version
    Then I should be given a temporal dependency network resolved to the most current timestamp


