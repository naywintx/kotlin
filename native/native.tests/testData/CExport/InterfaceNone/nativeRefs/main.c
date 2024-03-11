#include <stdint.h>

uintptr_t get_object();

uintptr_t dispose_object(uintptr_t);

_Bool compare_objects(uintptr_t, uintptr_t);

int main() {
    uintptr_t obj1 = get_object();
    uintptr_t obj2 = get_object();

    if (!compare_objects(obj1, obj2)) {
        return -1;
    }

    dispose_object(obj1);
    dispose_object(obj2);

    return 0;
}