import UIKit

extension UIView {
    var activeNavigationTransition: UIViewControllerTransitionCoordinator? {
        var responder = next
        while let current = responder {
            if let controller = current as? UIViewController {
                return controller.transitionCoordinator
            }
            responder = current.next
        }
        return nil
    }
}
