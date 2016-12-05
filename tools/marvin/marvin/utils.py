import copy
import sys


def string_input(input_txt='', termination=':', default=None,
                 allow_empty=False):
    while True:
        if default:
            print '%s%s [%s] ' % (  # noqa: E999
                input_txt, termination, default),
        else:
            print '%s%s ' % (input_txt, termination),
        answer = sys.stdin.readline().strip()
        if default and not answer:
            answer = default
        print '\r',
        # empty check
        if allow_empty or answer:
            break
        else:
            print 'Empty is not allowed.'

    return answer


def numerical_input(input_txt, min_value, max_value, default=False,
                    default_value=None):
    if default:
        def_value = min_value if not default_value else default_value
    else:
        def_value = None

    while True:
        try:
            value = int(string_input(input_txt, default=def_value))
            if min_value <= value <= max_value:
                break
        except (ValueError, NameError):
            pass
        print 'Please pick a number between', min_value, 'and', max_value
    return value


def boolean_input(question, default=True):
    resp = string_input(question, ' (Y/n)' if default else ' (y/N)',
                        allow_empty=True).lower()
    if not resp:
        return default
    else:
        return resp.__contains__('y')


def wait_for_enter(input_txt='Please press enter to continue.'):
    string_input(input_txt=input_txt, termination='', allow_empty=True)
