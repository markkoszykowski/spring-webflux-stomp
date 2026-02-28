Feature: STOMP WebFlux Server

  Background:
    Given the STOMP server is up


  Scenario: Bring down server
    Then the STOMP server is down


  Scenario: Client connects and disconnects
    When client 'A' connects to the hello world server successfully

    Then client 'A' disconnects


  Scenario: Client connects, subscribes, and disconnects
    When client 'A' connects to the hello world server successfully

    And client 'A' subscribes to 'test' with id '1'
    Then client 'A' receives:
      | Headers.content-type     | Headers.content-length | Payload      |
      | text/plain;charset=UTF-8 | 12                     | Hello World! |

    Then client 'A' disconnects


  Scenario: Duplicate subscription ids
    When client 'A' connects to the hello world server successfully

    And client 'A' subscribes to 'test' with id '1'
    And client 'A' subscribes to 'test' with id '1'

    Then client 'A' receives:
      | Headers.content-type     | Headers.content-length | Headers.message   | Payload      |
      | text/plain;charset=UTF-8 | 12                     |                   | Hello World! |
      |                          | 0                      | id already in use |              |
    And client 'A' is disconnected


  Scenario: Separate subscription ids
    When client 'A' connects to the hello world server successfully
    And client 'B' connects to the hello world server successfully

    And client 'A' subscribes to 'test' with id '1'
    And client 'B' subscribes to 'test' with id '1'

    Then client 'A' receives:
      | Headers.content-type     | Headers.content-length | Payload      |
      | text/plain;charset=UTF-8 | 12                     | Hello World! |
    And client 'B' receives:
      | Headers.content-type     | Headers.content-length | Payload      |
      | text/plain;charset=UTF-8 | 12                     | Hello World! |
